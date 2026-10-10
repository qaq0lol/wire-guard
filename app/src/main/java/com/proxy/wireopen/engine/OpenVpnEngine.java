package com.proxy.wireopen.engine;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.util.Log;

import com.proxy.wireopen.model.ProxyProfile;
import com.proxy.wireopen.util.CredentialManager;
import com.proxy.wireopen.util.LogManager;
import com.proxy.wireopen.util.SplitTunnelManager;

import java.util.HashSet;
import java.util.Set;

import java.io.StringReader;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.ConnectionStatus;
import de.blinkt.openvpn.core.OpenVPNService;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.core.VPNLaunchHelper;
import de.blinkt.openvpn.core.LogItem;
import de.blinkt.openvpn.core.VpnStatus;

/**
 * Industrial-grade OpenVPN Engine powered by ics-openvpn (OpenVPN 2.7 / OpenSSL 3.4.1).
 * Supports full TLS handshakes, AES/ChaCha20 ciphers, and secure user/password authentication.
 *
 * Credentials from CredentialManager are strictly applied ONLY here, completely isolated from WireGuard.
 */
public class OpenVpnEngine implements IVpnEngine, VpnStatus.StateListener, VpnStatus.ByteCountListener, VpnStatus.LogListener {
    private static final String TAG = "OpenVpnEngine";

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicLong rxBytes = new AtomicLong(0);
    private final AtomicLong txBytes = new AtomicLong(0);
    private ScheduledExecutorService timeoutExecutor;
    private volatile ScheduledFuture<?> connectTimeoutFuture;

    private Context appContext;
    private EngineCallback activeCallback;
    private volatile boolean connectedNotified = false;
    private volatile boolean hasLaunched = false;

    private static ScheduledExecutorService createTimeoutExecutor() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "WireOpen-OpenVPNTimeout");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public synchronized void start(VpnService vpnService, ProxyProfile profile, EngineCallback callback) throws Exception {
        if (isRunning.get()) {
            return;
        }

        if (timeoutExecutor == null || timeoutExecutor.isShutdown() || timeoutExecutor.isTerminated()) {
            timeoutExecutor = createTimeoutExecutor();
        }

        this.appContext = vpnService.getApplicationContext();
        this.activeCallback = callback;
        this.connectedNotified = false;
        this.hasLaunched = false;
        this.rxBytes.set(0);
        this.txBytes.set(0);

        String rawConfig = profile.getRawConfig();
        if (rawConfig == null || rawConfig.trim().isEmpty()) {
            throw new IllegalArgumentException("OpenVPN 配置文件内容为空");
        }

        LogManager.log(TAG, "正在解析 OpenVPN 官方配置文件...");
        ConfigParser parser = new ConfigParser();
        try {
            parser.parseConfig(new StringReader(rawConfig));
        } catch (Exception e) {
            throw new IllegalArgumentException("OpenVPN 配置文件格式错误: " + e.getMessage(), e);
        }

        VpnProfile vpnProfile = parser.convertProfile();
        if (vpnProfile == null) {
            throw new IllegalStateException("无法从配置文件生成合法的 OpenVPN 运行配置");
        }

        String nodeName = profile.getName() != null ? profile.getName() : "OpenVPN Node";
        vpnProfile.mName = nodeName;

        // Apply the same split-tunneling rules used by WireGuard.
        // ics-openvpn exposes package allow/disallow fields on VpnProfile;
        // the actual Android VpnService.Builder is created by OpenVPNService.
        applyAppRouting(vpnProfile, appContext);


        // Apply OpenVPN credentials strictly from CredentialManager (Isolated from WireGuard)
        CredentialManager credMgr = CredentialManager.getInstance();
        boolean configRequiresUserPass = requiresUserPass(rawConfig);
        boolean hasUserPass = false;

        if (configRequiresUserPass && credMgr != null && credMgr.hasCredentials()) {
            vpnProfile.mUsername = credMgr.getUsername();
            vpnProfile.mPassword = credMgr.getPassword();
            hasUserPass = true;
            LogManager.log(TAG, "已为 OpenVPN 会话注入认证账号");
        } else if (configRequiresUserPass
                && vpnProfile.mUsername != null && !vpnProfile.mUsername.isEmpty()) {
            hasUserPass = true;
        }

        boolean hasCerts = (vpnProfile.mClientCertFilename != null && !vpnProfile.mClientCertFilename.isEmpty()) ||
                           (vpnProfile.mPKCS12Filename != null && !vpnProfile.mPKCS12Filename.isEmpty());

        if (hasUserPass) {
            if (hasCerts) {
                vpnProfile.mAuthenticationType = VpnProfile.TYPE_USERPASS_CERTIFICATES;
            } else {
                vpnProfile.mAuthenticationType = VpnProfile.TYPE_USERPASS;
            }
        } else if (configRequiresUserPass) {
            // Configuration explicitly requires auth-user-pass, but no credentials provided
            throw new IllegalArgumentException("该 OpenVPN 节点需要账号密码认证");
        }

        // Do not force USERPASS for configurations that do not contain auth-user-pass.
        // Preserve the native parser's certificate/keystore authentication mode.

        // Keep tun open across reconnects
        vpnProfile.mPersistTun = true;

        LogManager.info(TAG, "注册 OpenVPN 原生日志与状态监听器...");
        VpnStatus.addStateListener(this);
        VpnStatus.addByteCountListener(this);
        VpnStatus.addLogListener(this);

        isRunning.set(true);

        try {
            ProfileManager.setTemporaryProfile(appContext, vpnProfile);
            VPNLaunchHelper.startOpenVpn(vpnProfile, appContext, "WireOpenProxy", false);
            scheduleConnectionTimeout(rawConfig, nodeName);
            this.hasLaunched = true;
            Log.i(TAG, "OpenVPN native tunnel launch requested for: " + nodeName);
            LogManager.info(TAG, "OpenVPN 官方原生引擎已启动 (OpenSSL 3.4 / OpenVPN 2.7 真实加密协商)");
        } catch (Exception e) {
            isRunning.set(false);
            hasLaunched = false;
            cancelConnectionTimeout();
            if (timeoutExecutor != null && !timeoutExecutor.isShutdown()) {
                try {
                    timeoutExecutor.shutdownNow();
                } catch (Exception ignored) {}
            }
            if (credMgr != null) credMgr.clearTemporaryCredentials();
            VpnStatus.removeStateListener(this);
            VpnStatus.removeByteCountListener(this);
            VpnStatus.removeLogListener(this);
            throw new RuntimeException("启动 OpenVPN 原生引擎失败: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void stop() {
        isRunning.set(false);
        hasLaunched = false;

        cancelTimeout();
        if (timeoutExecutor != null && !timeoutExecutor.isShutdown()) {
            try {
                timeoutExecutor.shutdownNow();
            } catch (Exception ignored) {}
        }

        VpnStatus.removeStateListener(this);
        VpnStatus.removeByteCountListener(this);
        VpnStatus.removeLogListener(this);

        if (appContext != null) {
            try {
                Intent disconnectIntent = new Intent(appContext, OpenVPNService.class);
                disconnectIntent.setAction(OpenVPNService.DISCONNECT_VPN);
                appContext.startService(disconnectIntent);
                Log.i(TAG, "OpenVPN disconnect intent sent");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping OpenVPN service", e);
            }
        }

        CredentialManager credMgr = CredentialManager.getInstance();
        if (credMgr != null) {
            credMgr.clearTemporaryCredentials();
        }
        connectedNotified = false;
        activeCallback = null;
        LogManager.log(TAG, "OpenVPN 原生隧道已停止");
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

    private static void applyAppRouting(VpnProfile vpnProfile, Context context) {
        if (vpnProfile == null || context == null) return;

        String mode = SplitTunnelManager.getRoutingMode(context);
        Set<String> packages = SplitTunnelManager.getSelectedPackages(context);
        Set<String> validPackages = new HashSet<>();
        android.content.pm.PackageManager pm = context.getPackageManager();

        if (packages != null) {
            for (String pkg : packages) {
                if (pkg == null || pkg.trim().isEmpty()) continue;
                try {
                    pm.getPackageInfo(pkg, 0);
                    validPackages.add(pkg);
                } catch (Exception ignored) {}
            }
        }

        if (vpnProfile.mAllowedAppsVpn == null) {
            vpnProfile.mAllowedAppsVpn = new HashSet<>();
        } else {
            vpnProfile.mAllowedAppsVpn.clear();
        }
        vpnProfile.mAllowAppVpnBypass = false;

        if (SplitTunnelManager.MODE_GLOBAL.equalsIgnoreCase(mode)) {
            vpnProfile.mAllowedAppsVpnAreDisallowed = true;
            return;
        }

        if (SplitTunnelManager.MODE_BYPASS.equalsIgnoreCase(mode)) {
            vpnProfile.mAllowedAppsVpnAreDisallowed = true;
            vpnProfile.mAllowedAppsVpn.addAll(validPackages);
            return;
        }

        // MODE_ALLOW: only selected third-party apps use the VPN.
        // Keep this app itself inside the VPN so the built-in IPPure WebView
        // and control traffic follow the selected VPN tunnel.
        vpnProfile.mAllowedAppsVpnAreDisallowed = false;
        vpnProfile.mAllowedAppsVpn.addAll(validPackages);
        vpnProfile.mAllowedAppsVpn.add(context.getPackageName());
    }

    /**
     * Returns the maximum amount of time the UI should remain in CONNECTING
     * for a single native OpenVPN connection attempt.
     *
     * TCP connections get a shorter timeout because a dead TCP endpoint can
     * otherwise remain in the native retry state indefinitely.
     */
    public static long getConnectAttemptTimeoutMillis(String rawConfig) {
        return isTcpConfig(rawConfig) ? 30_000L : 45_000L;
    }

    public static boolean isTcpConfig(String rawConfig) {
        if (rawConfig == null) return false;
        String normalized = rawConfig.toLowerCase(java.util.Locale.ROOT);
        return normalized.matches("(?s).*\\bproto\\s+tcp(?:[46])?(?:-client)?\\b.*")
                || normalized.matches("(?s).*\\bremote\\s+\\S+\\s+\\d+\\s+tcp(?:[46])?(?:-client)?\\b.*");
    }

    private void scheduleConnectionTimeout(String rawConfig, String nodeName) {
        cancelConnectionTimeout();
        final long timeoutMillis = getConnectAttemptTimeoutMillis(rawConfig);
        connectTimeoutFuture = timeoutExecutor.schedule(() -> {
            if (!isRunning.compareAndSet(true, false)) {
                return;
            }

            VpnStatus.removeStateListener(this);
            VpnStatus.removeByteCountListener(this);
            VpnStatus.removeLogListener(this);

            CredentialManager credMgr = CredentialManager.getInstance();
            if (credMgr != null) {
                credMgr.clearTemporaryCredentials();
            }

            try {
                if (appContext != null) {
                    Intent disconnectIntent = new Intent(appContext, OpenVPNService.class);
                    disconnectIntent.setAction(OpenVPNService.DISCONNECT_VPN);
                    appContext.startService(disconnectIntent);
                }
            } catch (Exception e) {
                Log.e(TAG, "OpenVPN timeout disconnect failed", e);
            }

            String transport = isTcpConfig(rawConfig) ? "TCP" : "UDP";
            long timeoutSeconds = timeoutMillis / 1000L;
            String friendlyErr = "OpenVPN " + transport + " 连接超时 (" + timeoutSeconds
                    + "秒内未收到连接成功状态，可能是端口受限、网络阻断或认证失败)";
            LogManager.error(TAG, "❌ " + friendlyErr + " - 节点: " + nodeName);

            EngineCallback callback = activeCallback;
            if (callback != null) {
                callback.onError(friendlyErr);
            }
        }, timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public synchronized void cancelTimeout() {
        cancelConnectionTimeout();
    }

    private synchronized void cancelConnectionTimeout() {
        ScheduledFuture<?> future = connectTimeoutFuture;
        connectTimeoutFuture = null;
        if (future != null) {
            future.cancel(true);
        }
    }

    public static boolean requiresUserPass(String rawConfig) {
        if (rawConfig == null) return false;
        String[] lines = rawConfig.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) continue;
            int comment = trimmed.indexOf('#');
            if (comment >= 0) trimmed = trimmed.substring(0, comment).trim();
            String[] parts = trimmed.split("\\s+");
            if (parts.length > 0 && "auth-user-pass".equalsIgnoreCase(parts[0])) {
                return true;
            }
        }
        return false;
    }

    // ─── VpnStatus.StateListener Callbacks ───────────────────────────────────

    private void finishFromNativeState(boolean notifyDisconnected) {
        if (!isRunning.getAndSet(false)) return;
        hasLaunched = false;
        cancelTimeout();
        if (timeoutExecutor != null && !timeoutExecutor.isShutdown()) {
            try {
                timeoutExecutor.shutdownNow();
            } catch (Exception ignored) {}
        }
        VpnStatus.removeStateListener(this);
        VpnStatus.removeByteCountListener(this);
        VpnStatus.removeLogListener(this);
        CredentialManager credMgr = CredentialManager.getInstance();
        if (credMgr != null) {
            credMgr.clearTemporaryCredentials();
        }
        connectedNotified = false;
        EngineCallback callback = activeCallback;
        activeCallback = null;
        if (notifyDisconnected && callback != null) {
            callback.onDisconnected();
        }
    }

    @Override
    public void updateState(String state, String logmessage, int localizedResId, ConnectionStatus level, Intent intent) {
        String msg = (logmessage != null && !logmessage.isEmpty()) ? logmessage : (state != null ? state : "");
        Log.i(TAG, "OpenVPN state: " + state + ", level: " + level + ", message: " + msg);
        if (!msg.isEmpty()) {
            LogManager.info(TAG, "[" + state + "] " + msg);
        }

        if (activeCallback == null || !isRunning.get()) {
            return;
        }

        if (level == ConnectionStatus.LEVEL_CONNECTED) {
            cancelConnectionTimeout();
            if (!connectedNotified) {
                connectedNotified = true;
                LogManager.info(TAG, "🎉 OpenVPN 隧道握手完成，安全连接已正式建立");
                activeCallback.onConnected();
            }
        } else if (level == ConnectionStatus.LEVEL_NOTCONNECTED || level == ConnectionStatus.LEVEL_NONETWORK) {
            cancelTimeout();
            if (hasLaunched) {
                if (timeoutExecutor != null && !timeoutExecutor.isShutdown()) {
                    try {
                        timeoutExecutor.shutdownNow();
                    } catch (Exception ignored) {}
                }
                if (connectedNotified) {
                    LogManager.warn(TAG, "OpenVPN 连接已中断: " + msg);
                    finishFromNativeState(true);
                } else {
                    LogManager.info(TAG, "OpenVPN 未连接/握手已取消: " + msg);
                    finishFromNativeState(true);
                }
            }
        } else if (level == ConnectionStatus.LEVEL_AUTH_FAILED) {
            EngineCallback cb = activeCallback;
            finishFromNativeState(false);
            String authErr = "OpenVPN 认证失败 (账号/密码或证书错误)";
            LogManager.error(TAG, authErr + ": " + msg);
            if (cb != null) {
                cb.onError(authErr);
            }
        }
    }

    @Override
    public void setConnectedVPN(String uuid) {
        // Tracked via updateState
    }

    // ─── VpnStatus.LogListener Callbacks ─────────────────────────────────────

    @Override
    public void newLog(LogItem logItem) {
        if (logItem == null) return;
        String msg = logItem.getString(appContext);
        if (msg == null || msg.trim().isEmpty()) return;

        LogManager.Level level = LogManager.Level.INFO;
        if (logItem.getLogLevel() != null) {
            switch (logItem.getLogLevel()) {
                case ERROR:
                    level = LogManager.Level.ERROR;
                    break;
                case WARNING:
                    level = LogManager.Level.WARN;
                    break;
                case VERBOSE:
                case DEBUG:
                    level = LogManager.Level.DEBUG;
                    break;
                case INFO:
                default:
                    level = LogManager.Level.INFO;
                    break;
            }
        }
        LogManager.log(level, "OVPN-CORE", msg.trim());
    }

    // ─── VpnStatus.ByteCountListener Callbacks ───────────────────────────────

    @Override
    public void updateByteCount(long inBytes, long outBytes, long diffIn, long diffOut) {
        rxBytes.set(inBytes);
        txBytes.set(outBytes);

        if (activeCallback != null && isRunning.get()) {
            activeCallback.onTrafficUpdate(inBytes, outBytes);
        }
    }
}
