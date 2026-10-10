package com.proxy.wireopen.engine;

import android.content.Context;
import android.net.VpnService;
import android.util.Log;

import com.proxy.wireopen.model.ProxyProfile;
import com.proxy.wireopen.model.WireGuardConfig;
import com.proxy.wireopen.parser.WireGuardConfigParser;
import com.proxy.wireopen.util.LogManager;
import com.proxy.wireopen.util.SplitTunnelManager;
import com.wireguard.android.backend.Backend;
import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Statistics;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;
import com.wireguard.config.InetAddresses;
import com.wireguard.config.InetNetwork;
import com.wireguard.config.ParseException;
import com.wireguard.config.Interface;
import com.wireguard.config.Peer;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tunnel engine for WireGuard protocol backed by official WireGuard GoBackend.
 * Implements standard Noise_IKpsk2 handshake, ChaCha20-Poly1305 encryption,
 * Curve25519 key exchange, and Blake2s MAC/PSK authentication via wireguard-go.
 */
public class WireGuardEngine implements IVpnEngine {
    private static final String TAG = "WireGuardEngine";
    private static final String TUNNEL_NAME = "wireopen";

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean disconnectedFired = new AtomicBoolean(false);
    private final AtomicBoolean suppressDownCallback = new AtomicBoolean(false);
    private final AtomicBoolean startInProgress = new AtomicBoolean(false);
    private final AtomicLong rxBytes = new AtomicLong(0);
    private final AtomicLong txBytes = new AtomicLong(0);

    private volatile Backend backend;
    private volatile Tunnel tunnel;
    private Thread statsThread;

    @Override
    public synchronized void start(VpnService vpnService, ProxyProfile profile, EngineCallback callback) throws Exception {
        if (isRunning.get()) {
            return;
        }

        Context context = vpnService.getApplicationContext();
        Config wgConfig = buildConfig(context, profile);

        Log.i(TAG, "Initializing WireGuard GoBackend...");
        LogManager.log(TAG, "正在初始化 WireGuard 官方内核引擎 (wireguard-go)...");
        LogManager.log(TAG, "WireGuard 协议引擎启动: 基于 Curve25519 密钥对与 Noise_IKpsk2 协商，与账号密码凭据彻底绝缘。");

        // Fix: GoBackend must receive the VpnService instance itself, not ApplicationContext.
        // GoBackend internally calls VpnService.Builder which requires a VpnService subclass.
        backend = new GoBackend(vpnService);

        tunnel = new Tunnel() {
            @Override
            public String getName() {
                return TUNNEL_NAME;
            }

            @Override
            public void onStateChange(Tunnel.State newState) {
                Log.i(TAG, "Tunnel state changed: " + newState);
                LogManager.log(TAG, "WireGuard 隧道状态变更: " + newState);
                if (newState == Tunnel.State.UP) {
                    isRunning.set(true);
                    disconnectedFired.set(false);
                    callback.onConnected();
                } else if (newState == Tunnel.State.DOWN) {
                    isRunning.set(false);
                    stopStatsLoop();
                    backend = null;
                    tunnel = null;
                    if (!suppressDownCallback.get() && !startInProgress.get()
                            && disconnectedFired.compareAndSet(false, true)) {
                        callback.onDisconnected();
                    }
                }
            }
        };

        disconnectedFired.set(false);
        suppressDownCallback.set(false);
        isRunning.set(true);

        final String epDisplay = profile.getEndpointDisplay();
        startInProgress.set(true);
        try {
            Log.i(TAG, "Activating WireGuard tunnel with GoBackend: " + epDisplay);
            LogManager.info(TAG, "启动 WireGuard 隧道协商: " + epDisplay);
            backend.setState(tunnel, Tunnel.State.UP, wgConfig);
        } catch (Exception e) {
            startInProgress.set(false);
            suppressDownCallback.set(true);
            Log.e(TAG, "Failed to start WireGuard tunnel: " + e.getMessage(), e);
            String raw = (e.getMessage() != null) ? e.getMessage() : "";
            String errorDesc;
            if (raw.toLowerCase(java.util.Locale.ROOT).contains("unknownhost") || raw.contains("No address associated")) {
                errorDesc = "无法解析远端服务器域名 (DNS异常)";
            } else if (raw.contains("Cannot assign requested address") || raw.contains("bad address")) {
                errorDesc = "配置内网 IP 地址无效或网段冲突";
            } else if (raw.toLowerCase(java.util.Locale.ROOT).contains("permission")) {
                errorDesc = "VPN 虚拟网卡创建被系统拒绝 (权限冲突)";
            } else {
                errorDesc = "WireGuard 启动失败: " + (raw.isEmpty() ? e.getClass().getSimpleName() : raw);
            }
            LogManager.error(TAG, errorDesc);
            isRunning.set(false);
            cleanupBackend();
            throw new Exception(errorDesc, e);
        }

        startInProgress.set(false);
        // If GoBackend reported DOWN during launch, report one startup error rather
        // than racing an onDisconnected callback against the service's onError path.
        if (!isRunning.get()) {
            suppressDownCallback.set(true);
            cleanupBackend();
            throw new IllegalStateException("WireGuard 隧道启动后立即断开 (可能TUN接口被占用)");
        }

        // Start real-time traffic statistics and handshake watchdog polling loop
        statsThread = new Thread(() -> runStatsLoop(callback, epDisplay), "WireGuard-StatsLoop");
        statsThread.start();
    }

    private void runStatsLoop(EngineCallback callback, String epDisplay) {
        long startMillis = System.currentTimeMillis();
        boolean inboundTrafficObserved = false;
        boolean noInboundWarningLogged = false;

        while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(1000);
                if (!isRunning.get()) break;

                Backend currentBackend = backend;
                Tunnel currentTunnel = tunnel;
                if (currentBackend != null && currentTunnel != null) {
                    Statistics stats = currentBackend.getStatistics(currentTunnel);
                    if (stats != null) {
                        long rx = stats.totalRx();
                        long tx = stats.totalTx();
                        rxBytes.set(rx);
                        txBytes.set(tx);
                        callback.onTrafficUpdate(rx, tx);

                        if (rx > 0 && !inboundTrafficObserved) {
                            inboundTrafficObserved = true;
                            LogManager.info(TAG, "WireGuard 已收到隧道流量响应（Rx=" + rx + "B）");
                        }
                        // The byte counters are not a definitive handshake signal. An idle
                        // or one-way tunnel may legitimately have Rx=0, so don't tear down an
                        // otherwise-UP tunnel based on that heuristic.
                        if (!inboundTrafficObserved) {
                            long elapsedSec = (System.currentTimeMillis() - startMillis) / 1000L;
                            if (elapsedSec >= 15 && tx > 0 && !noInboundWarningLogged) {
                                noInboundWarningLogged = true;
                                LogManager.warn(TAG, "已发送隧道流量但尚未观察到接收流量；保持连接，不能仅凭 Rx=0 判定握手失败。节点: " + epDisplay);
                            }
                        }
                    }
                }
            } catch (InterruptedException ignored) {
                break;
            } catch (Exception e) {
                Log.w(TAG, "Error fetching statistics: " + e.getMessage());
            }
        }
    }

    public static Config buildConfig(Context context, ProxyProfile profile) throws Exception {
        WireGuardConfig config = profile.getWireGuardConfig();
        if (config == null && profile.getRawConfig() != null) {
            config = WireGuardConfigParser.parse(profile.getRawConfig());
            profile.setWireGuardConfig(config);
        }

        if (config == null || !config.isValid()) {
            throw new IllegalArgumentException("WireGuard 配置无效或缺少必要字段");
        }

        return toWireGuardConfig(context, config);
    }

    public static Config toWireGuardConfig(Context context, WireGuardConfig config) throws Exception {
        LogManager.info(TAG, "WireGuard 节点目标: " + config.getFullEndpoint());
        LogManager.debug(TAG, "内网接口地址: " + config.getAddresses() + " | MTU: " + (config.getMtu() > 0 ? config.getMtu() : 1280));
        LogManager.debug(TAG, "DNS 服务器: " + (config.getDnsServers().isEmpty() ? "[1.1.1.1, 8.8.8.8]" : config.getDnsServers()));
        String pubKey = config.getPublicKey();
        String maskedKey = (pubKey != null && pubKey.length() > 8) ? pubKey.substring(0, 8) + "..." : "******";
        LogManager.debug(TAG, "远端服务端公钥: " + maskedKey);

        Interface.Builder ifaceBuilder = new Interface.Builder();
        ifaceBuilder.parsePrivateKey(config.getPrivateKey().trim());

        for (String addr : config.getAddresses()) {
            String trimmed = addr.trim();
            if (!trimmed.isEmpty()) {
                ifaceBuilder.addAddress(InetNetwork.parse(trimmed));
            }
        }

        if (config.getDnsServers().isEmpty()) {
            try {
                ifaceBuilder.addDnsServer(InetAddresses.parse("1.1.1.1"));
                ifaceBuilder.addDnsServer(InetAddresses.parse("8.8.8.8"));
            } catch (Exception ignored) {}
        } else {
            for (String dns : config.getDnsServers()) {
                String trimmed = dns.trim();
                if (!trimmed.isEmpty()) {
                    try {
                        ifaceBuilder.addDnsServer(InetAddresses.parse(trimmed));
                    } catch (ParseException e) {
                        Log.w(TAG, "Skipping invalid DNS server: " + trimmed);
                    }
                }
            }
        }

        if (config.getListenPort() > 0) {
            ifaceBuilder.setListenPort(config.getListenPort());
        }

        if (config.getMtu() > 0) {
            ifaceBuilder.setMtu(config.getMtu());
        }

        // Apply split tunneling policies
        if (context != null) {
            String mode = SplitTunnelManager.getRoutingMode(context);
            Set<String> packages = SplitTunnelManager.getSelectedPackages(context);
            android.content.pm.PackageManager pm = context.getPackageManager();

            if (SplitTunnelManager.MODE_BYPASS.equalsIgnoreCase(mode)) {
                if (packages != null && !packages.isEmpty()) {
                    Set<String> validBypass = new HashSet<>();
                    for (String pkg : packages) {
                        try {
                            pm.getPackageInfo(pkg, 0);
                            validBypass.add(pkg);
                        } catch (Exception ignored) {}
                    }
                    if (!validBypass.isEmpty()) {
                        ifaceBuilder.excludeApplications(validBypass);
                    }
                }
            } else if (SplitTunnelManager.MODE_ALLOW.equalsIgnoreCase(mode)) {
                Set<String> allowed = new HashSet<>();
                if (packages != null) {
                    for (String pkg : packages) {
                        try {
                            pm.getPackageInfo(pkg, 0);
                            allowed.add(pkg);
                        } catch (Exception ignored) {}
                    }
                }
                allowed.add(context.getPackageName());
                ifaceBuilder.includeApplications(allowed);
            }
        }

        Peer.Builder peerBuilder = new Peer.Builder();
        peerBuilder.parsePublicKey(config.getPublicKey().trim());

        if (config.getPresharedKey() != null && !config.getPresharedKey().trim().isEmpty()) {
            peerBuilder.parsePreSharedKey(config.getPresharedKey().trim());
        }

        peerBuilder.parseEndpoint(config.getFullEndpoint().trim());

        if (config.getAllowedIps() != null && !config.getAllowedIps().isEmpty()) {
            for (String allowedIp : config.getAllowedIps()) {
                String trimmed = allowedIp.trim();
                if (!trimmed.isEmpty()) {
                    peerBuilder.addAllowedIp(InetNetwork.parse(trimmed));
                }
            }
        } else {
            peerBuilder.addAllowedIp(InetNetwork.parse("0.0.0.0/0"));
            peerBuilder.addAllowedIp(InetNetwork.parse("::/0"));
        }

        if (config.getPersistentKeepalive() > 0) {
            peerBuilder.setPersistentKeepalive(config.getPersistentKeepalive());
        }

        return new Config.Builder()
                .setInterface(ifaceBuilder.build())
                .addPeer(peerBuilder.build())
                .build();
    }

    private synchronized void stopStatsLoop() {
        if (statsThread != null) {
            statsThread.interrupt();
            statsThread = null;
        }
    }

    private synchronized void cleanupBackend() {
        if (backend != null && tunnel != null) {
            try {
                backend.setState(tunnel, Tunnel.State.DOWN, null);
            } catch (Exception e) {
                Log.w(TAG, "Error cleaning WireGuard backend: " + e.getMessage());
            }
        }
        backend = null;
        tunnel = null;
    }

    @Override
    public synchronized void stop() {
        isRunning.set(false);
        stopStatsLoop();

        if (backend != null || tunnel != null) {
            Log.i(TAG, "Tearing down WireGuard tunnel...");
            LogManager.log(TAG, "正在关闭 WireGuard 隧道...");
            cleanupBackend();
        }

        Log.i(TAG, "WireGuard tunnel stopped");
    }

    @Override
    public boolean isRunning() {
        return isRunning.get();
    }

    @Override
    public long getRxBytes() {
        return rxBytes.get();
    }

    @Override
    public long getTxBytes() {
        return txBytes.get();
    }
}
