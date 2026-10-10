package com.proxy.wireopen.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.proxy.wireopen.MainActivity;
import com.proxy.wireopen.engine.IVpnEngine;
import com.proxy.wireopen.engine.OpenVpnEngine;
import com.proxy.wireopen.engine.WireGuardEngine;
import com.proxy.wireopen.model.ConnectionState;
import com.proxy.wireopen.model.ProtocolType;
import com.proxy.wireopen.model.ProxyProfile;
import com.proxy.wireopen.util.LogManager;
import com.proxy.wireopen.util.TrafficStatsManager;

import de.blinkt.openvpn.core.OpenVPNService;

/**
 * Unified Android VpnService supporting both WireGuard and OpenVPN protocols.
 * OpenVPN delegates TUN ownership to ics-openvpn's native OpenVPNService.
 */
public class UnifiedVpnService extends VpnService {
    public static final String ACTION_START_VPN = "com.proxy.wireopen.START_VPN";
    public static final String ACTION_STOP_VPN = "com.proxy.wireopen.STOP_VPN";
    public static final String ACTION_STATE_CHANGED = "com.proxy.wireopen.STATE_CHANGED";
    public static final String ACTION_TRAFFIC_UPDATE = "com.proxy.wireopen.TRAFFIC_UPDATE";

    public static final String EXTRA_PROFILE = "extra_profile";
    public static final String EXTRA_STATE = "extra_state";
    public static final String EXTRA_ERROR_MESSAGE = "extra_error_message";
    public static final String EXTRA_RX_BYTES = "extra_rx_bytes";
    public static final String EXTRA_TX_BYTES = "extra_tx_bytes";

    private static final String CHANNEL_ID = "wireopen_vpn_channel";
    private static final int NOTIFICATION_ID = 1001;

    private static volatile ConnectionState currentState = ConnectionState.DISCONNECTED;
    private static volatile String lastErrorMessage = null;
    private static volatile ProxyProfile currentProfile = null;
    private static volatile boolean isOpenVpnHandoff = false;
    private static volatile long tunnelGeneration = 0L;
    private static volatile ProtocolType lastRunningProtocol = null;
    private static volatile long lastEngineStopTime = 0L;

    private static volatile IVpnEngine activeEngine;

    public static ConnectionState getCurrentState() {
        return currentState;
    }

    public static ProxyProfile getCurrentProfile() {
        return currentProfile;
    }

    public static String getLastErrorMessage() {
        return lastErrorMessage;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if (ACTION_START_VPN.equals(action)) {
            ProxyProfile profile = (ProxyProfile) intent.getSerializableExtra(EXTRA_PROFILE);
            if (profile != null) {
                handleStartVpn(profile);
            }
        } else if (ACTION_STOP_VPN.equals(action)) {
            stopTunnel();
        }
        return START_NOT_STICKY;
    }

    private synchronized void startTunnel(ProxyProfile profile) {
        handleStartVpn(profile);
    }

    private synchronized void handleStartVpn(ProxyProfile profile) {
        final long generation = ++tunnelGeneration;
        isOpenVpnHandoff = false;

        ProtocolType previousProtocol = currentProfile != null
                ? currentProfile.getProtocolType() : lastRunningProtocol;
        boolean isHeterogeneous = (currentProfile != null && currentProfile.getProtocolType() != profile.getProtocolType())
                || (lastRunningProtocol != null && lastRunningProtocol != profile.getProtocolType()
                    && (System.currentTimeMillis() - lastEngineStopTime < 2500L));
        IVpnEngine oldEngine = activeEngine;
        activeEngine = null;

        if (oldEngine != null) {
            if (currentProfile != null) {
                lastRunningProtocol = currentProfile.getProtocolType();
            }
            try {
                LogManager.log("UnifiedVpnService", "正在停止前序 VPN 引擎...");
                oldEngine.stop();
            } catch (Exception e) {
                LogManager.log("UnifiedVpnService", "停止旧 VPN 引擎失败: " + e.getMessage());
            }
            lastEngineStopTime = System.currentTimeMillis();
        }

        // Ensure a previous native OpenVPN service is no longer holding the TUN.
        // OpenVpnEngine.stop() already sends the native disconnect request.
        // Only send a direct request if a native OpenVPN handoff has no engine object.
        if (oldEngine == null && previousProtocol == ProtocolType.OPENVPN) {
            disconnectNativeOpenVpn();
        }

        currentProfile = profile;
        lastRunningProtocol = profile.getProtocolType();
        updateState(ConnectionState.CONNECTING);
        LogManager.log("UnifiedVpnService",
                "正在启动代理服务: " + profile.getName() + " [" + profile.getProtocolType() + "]");

        new Thread(() -> {
            // 安全重叠互斥等待：确保前序虚拟网卡与 Native 进程完全释放后再创建新网卡，杜绝 IllegalStateException
            long elapsed = System.currentTimeMillis() - lastEngineStopTime;
            long requiredWait = isHeterogeneous ? 800L : (oldEngine != null ? 400L : 0L);
            if (elapsed < requiredWait) {
                try {
                    long waitMs = requiredWait - elapsed;
                    LogManager.info("UnifiedVpnService", "跨协议切换/前序引擎释放中，互斥等待网卡释放 (" + waitMs + "ms)...");
                    Thread.sleep(waitMs);
                } catch (InterruptedException ignored) {}
            }

            if (generation != tunnelGeneration) {
                LogManager.log("UnifiedVpnService", "隧道启动请求已过期，放弃启动");
                return;
            }

            if (profile.getProtocolType() == ProtocolType.OPENVPN) {
                startOpenVpn(profile, generation);
            } else {
                startWireGuard(profile, generation);
            }
        }, "WireOpen-TunnelTransitionWorker").start();
    }

    private void startOpenVpn(ProxyProfile profile, long generation) {
        final OpenVpnEngine ovpnEngine = new OpenVpnEngine();

        Notification notification = buildNotification("正在启动 OpenVPN...", profile.getName());
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                );
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception fgsEx) {
            LogManager.log("UnifiedVpnService",
                    "OpenVPN 前台服务启动提示: " + fgsEx.getMessage());
        }

        activeEngine = ovpnEngine;

        new Thread(() -> {
            boolean launched = false;
            try {
                ovpnEngine.start(this, profile, new IVpnEngine.EngineCallback() {
                    @Override
                    public void onConnected() {
                        if (generation != tunnelGeneration) return;
                        currentState = ConnectionState.CONNECTED;
                        updateState(ConnectionState.CONNECTED);
                        LogManager.log("UnifiedVpnService", "OpenVPN 连接成功建立");
                    }

                    @Override
                    public void onDisconnected() {
                        if (generation != tunnelGeneration) return;
                        if (currentProfile != null) {
                            lastRunningProtocol = currentProfile.getProtocolType();
                        }
                        lastEngineStopTime = System.currentTimeMillis();
                        activeEngine = null;
                        currentState = ConnectionState.DISCONNECTED;
                        currentProfile = null;
                        updateState(ConnectionState.DISCONNECTED);
                        LogManager.log("UnifiedVpnService", "OpenVPN 连接已断开");
                    }

                    @Override
                    public void onError(String message) {
                        if (generation != tunnelGeneration) return;
                        if (currentProfile != null) {
                            lastRunningProtocol = currentProfile.getProtocolType();
                        }
                        lastEngineStopTime = System.currentTimeMillis();
                        activeEngine = null;
                        currentState = ConnectionState.ERROR;
                        String err = (message != null && !message.trim().isEmpty()) ? message : "OpenVPN 代理连接异常";
                        updateState(ConnectionState.ERROR, err);
                        LogManager.error("UnifiedVpnService", "OpenVPN 错误: " + err);
                    }

                    @Override
                    public void onTrafficUpdate(long rxBytes, long txBytes) {
                        if (generation != tunnelGeneration) return;
                        broadcastTraffic(rxBytes, txBytes);
                    }
                });
                launched = true;
            } catch (Exception e) {
                String errMsg = "OpenVPN 启动失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                LogManager.error("UnifiedVpnService", errMsg);
                if (generation == tunnelGeneration) {
                    activeEngine = null;
                    currentState = ConnectionState.ERROR;
                    updateState(ConnectionState.ERROR, errMsg);
                }
            }

            if (generation != tunnelGeneration) {
                ovpnEngine.stop();
                return;
            }

            if (launched) {
                isOpenVpnHandoff = true;
                removeForegroundNotification();
                stopSelf();
            } else {
                isOpenVpnHandoff = false;
                removeForegroundNotification();
                stopSelf();
            }
        }, "WireOpen-OpenVPNLauncher").start();
    }

    private void startWireGuard(ProxyProfile profile, long generation) {
        Notification notification = buildNotification("正在连接代理...", profile.getName());
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                );
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception fgsEx) {
            LogManager.log("UnifiedVpnService",
                    "WireGuard 前台服务启动提示: " + fgsEx.getMessage());
        }

        final WireGuardEngine wgEngine = new WireGuardEngine();
        activeEngine = wgEngine;

        new Thread(() -> {
            try {
                wgEngine.start(this, profile, new IVpnEngine.EngineCallback() {
                    @Override
                    public void onConnected() {
                        if (generation != tunnelGeneration) return;
                        currentState = ConnectionState.CONNECTED;
                        updateState(ConnectionState.CONNECTED);
                        LogManager.log("UnifiedVpnService", "WireGuard 代理已成功建立连接");
                        updateNotification(
                                "已连接: " + profile.getName(),
                                "节点: " + profile.getEndpointDisplay()
                        );
                    }

                    @Override
                    public void onDisconnected() {
                        if (generation != tunnelGeneration) return;
                        if (currentProfile != null) {
                            lastRunningProtocol = currentProfile.getProtocolType();
                        }
                        lastEngineStopTime = System.currentTimeMillis();
                        activeEngine = null;
                        currentState = ConnectionState.DISCONNECTED;
                        currentProfile = null;
                        updateState(ConnectionState.DISCONNECTED);
                        LogManager.log("UnifiedVpnService", "WireGuard 代理连接已断开");
                        removeForegroundNotification();
                        stopSelf();
                    }

                    @Override
                    public void onError(String message) {
                        if (generation != tunnelGeneration) return;
                        if (currentProfile != null) {
                            lastRunningProtocol = currentProfile.getProtocolType();
                        }
                        lastEngineStopTime = System.currentTimeMillis();
                        activeEngine = null;
                        currentState = ConnectionState.ERROR;
                        String err = (message != null && !message.trim().isEmpty()) ? message : "WireGuard 隧道握手或配置异常";
                        updateState(ConnectionState.ERROR, err);
                        LogManager.error("UnifiedVpnService",
                                "WireGuard 代理发生错误: " + err);
                        updateNotification("代理连接出错", err);
                    }

                    @Override
                    public void onTrafficUpdate(long rxBytes, long txBytes) {
                        if (generation != tunnelGeneration) return;
                        broadcastTraffic(rxBytes, txBytes);
                    }
                });
            } catch (Exception e) {
                if (generation != tunnelGeneration) return;
                if (currentProfile != null) {
                    lastRunningProtocol = currentProfile.getProtocolType();
                }
                lastEngineStopTime = System.currentTimeMillis();
                activeEngine = null;
                currentState = ConnectionState.ERROR;
                String err = "WireGuard 启动失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                updateState(ConnectionState.ERROR, err);
                LogManager.error("UnifiedVpnService", err);
                removeForegroundNotification();
                stopSelf();
            }
        }, "WireOpen-WireGuardLauncher").start();
    }

    private synchronized void stopTunnel() {
        ++tunnelGeneration;
        isOpenVpnHandoff = false;

        updateState(ConnectionState.DISCONNECTING);
        LogManager.log("UnifiedVpnService", "正在断开代理连接...");

        if (currentProfile != null) {
            lastRunningProtocol = currentProfile.getProtocolType();
        }
        ProtocolType previousProtocol = currentProfile != null
                ? currentProfile.getProtocolType() : lastRunningProtocol;
        lastEngineStopTime = System.currentTimeMillis();

        IVpnEngine engineToStop = activeEngine;
        if (engineToStop != null) {
            try {
                engineToStop.stop();
            } catch (Exception e) {
                LogManager.log("UnifiedVpnService", "停止 VPN 引擎失败: " + e.getMessage());
            }
            activeEngine = null;
        }

        // The engine owns its cleanup. Only directly stop a handed-off native
        // OpenVPN service when its engine object is no longer available.
        if (engineToStop == null && previousProtocol == ProtocolType.OPENVPN) {
            disconnectNativeOpenVpn();
        }

        currentState = ConnectionState.DISCONNECTED;
        currentProfile = null;
        updateState(ConnectionState.DISCONNECTED);
        removeForegroundNotification();
        stopSelf();
    }

    private void disconnectNativeOpenVpn() {
        try {
            Intent disconnectIntent = new Intent();
            disconnectIntent.setClassName(
                    getPackageName(),
                    "de.blinkt.openvpn.core.OpenVPNService"
            );
            disconnectIntent.setAction(OpenVPNService.DISCONNECT_VPN);
            startService(disconnectIntent);
        } catch (Exception e) {
            LogManager.log("UnifiedVpnService",
                    "发送 OpenVPN 断连请求失败: " + e.getMessage());
        }
    }

    @Override
    public void onRevoke() {
        LogManager.log("UnifiedVpnService", "系统 VPN 权限接口回调");
        if (isOpenVpnHandoff
                || (currentProfile != null
                && currentProfile.getProtocolType() == ProtocolType.OPENVPN)) {
            LogManager.log("UnifiedVpnService",
                    "当前为 OpenVPN 原生服务交接场景，忽略统一服务的 TUN 撤销回调");
            return;
        }

        stopTunnel();
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        if (isOpenVpnHandoff) {
            isOpenVpnHandoff = false;
            LogManager.log("UnifiedVpnService",
                    "OpenVPN 核心已交由 OpenVPNService 独立持有，统一服务正常退出");
            super.onDestroy();
            return;
        }

        // Avoid recursive stopTunnel() from onDestroy after an explicit stop.
        if (currentState != ConnectionState.DISCONNECTED) {
            stopTunnel();
        }
        super.onDestroy();
    }

    private void removeForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
    }

    private void updateState(ConnectionState state) {
        updateState(state, null);
    }

    private void updateState(ConnectionState state, String errorMessage) {
        currentState = state;
        if (state == ConnectionState.ERROR) {
            lastErrorMessage = errorMessage;
        } else if (state == ConnectionState.CONNECTED) {
            lastErrorMessage = null;
        }
        Intent intent = new Intent(ACTION_STATE_CHANGED);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_STATE, state.name());
        if (errorMessage != null && !errorMessage.trim().isEmpty()) {
            intent.putExtra(EXTRA_ERROR_MESSAGE, errorMessage);
        }
        sendBroadcast(intent);
    }

    private void broadcastTraffic(long rxBytes, long txBytes) {
        Intent intent = new Intent(ACTION_TRAFFIC_UPDATE);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_RX_BYTES, Math.max(0, rxBytes));
        intent.putExtra(EXTRA_TX_BYTES, Math.max(0, txBytes));
        sendBroadcast(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "VPN 代理运行状态",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("显示 WireGuard 和 OpenVPN 代理服务运行状态");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String title, String content) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Intent stopIntent = new Intent(this, UnifiedVpnService.class);
        stopIntent.setAction(ACTION_STOP_VPN);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(com.proxy.wireopen.R.drawable.ic_launcher)
                .setContentIntent(pendingIntent)
                .addAction(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        "断开连接",
                        stopPendingIntent
                )
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification(String title, String content) {
        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification(title, content));
        }
    }
}
