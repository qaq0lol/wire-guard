package com.proxy.wireopen.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/**
 * TrafficStatsManager manages:
 * 1. Historical all-time traffic statistics with reset capability.
 * 2. Real-time current session statistics.
 */
public class TrafficStatsManager {
    private static final String PREF_NAME = "wireopen_traffic_stats";
    private static final String KEY_ALL_TIME_RX = "all_time_rx_bytes";
    private static final String KEY_ALL_TIME_TX = "all_time_tx_bytes";
    private static final String KEY_CONNECT_COUNT = "connect_count";

    private static TrafficStatsManager instance;
    private final SharedPreferences prefs;

    // In-memory session stats
    private long sessionRxBytes = 0;
    private long sessionTxBytes = 0;
    private long sessionStartTime = 0;
    private boolean sessionActive = false;

    // Debounce synchronization for all-time stats to reduce disk I/O
    private static final long PERSIST_INTERVAL_MS = 15000L;
    private long lastPersistTimeMs = 0;
    private long pendingDeltaRx = 0;
    private long pendingDeltaTx = 0;

    private String outboundIp = "--";
    private String remoteEndpoint = "--";
    private String protocolChannel = "--";
    private String virtualTunIp = "--";
    private double currentDownSpeed = 0.0;
    private double currentUpSpeed = 0.0;

    public static synchronized void init(Context context) {
        if (instance == null) {
            instance = new TrafficStatsManager(context.getApplicationContext());
        }
    }

    public static synchronized TrafficStatsManager getInstance() {
        return instance;
    }

    private TrafficStatsManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (!prefs.contains(KEY_ALL_TIME_RX)) {
            prefs.edit()
                    .putLong(KEY_ALL_TIME_RX, 0L)
                    .putLong(KEY_ALL_TIME_TX, 0L)
                    .putInt(KEY_CONNECT_COUNT, 0)
                    .apply();
        }
    }

    // ─── Session lifecycle ───────────────────────────────────────────────────

    public synchronized void startSession(String endpoint, String protocol, String tunIp) {
        if (sessionActive) return;
        flushPendingTraffic();
        this.sessionActive = true;
        this.sessionStartTime = System.currentTimeMillis();
        this.sessionRxBytes = 0;
        this.sessionTxBytes = 0;
        this.pendingDeltaRx = 0;
        this.pendingDeltaTx = 0;
        this.lastPersistTimeMs = System.currentTimeMillis();
        this.remoteEndpoint = endpoint != null ? endpoint : "--";
        this.protocolChannel = protocol != null ? protocol : "--";
        this.virtualTunIp = tunIp != null ? tunIp : "--";

        prefs.edit().putInt(KEY_CONNECT_COUNT, prefs.getInt(KEY_CONNECT_COUNT, 0) + 1).apply();
    }

    public synchronized void updateSessionTraffic(long rxBytes, long txBytes) {
        if (!sessionActive) return;
        rxBytes = Math.max(0, rxBytes);
        txBytes = Math.max(0, txBytes);
        long deltaRx = Math.max(0, rxBytes - this.sessionRxBytes);
        long deltaTx = Math.max(0, txBytes - this.sessionTxBytes);
        this.sessionRxBytes = rxBytes;
        this.sessionTxBytes = txBytes;
        if (deltaRx > 0 || deltaTx > 0) {
            this.pendingDeltaRx += deltaRx;
            this.pendingDeltaTx += deltaTx;
            long now = System.currentTimeMillis();
            if (now - lastPersistTimeMs >= PERSIST_INTERVAL_MS) {
                flushPendingTraffic();
            }
        }
    }

    public synchronized void updateSessionTraffic(long rxBytes, long txBytes, double downSpeed, double upSpeed) {
        this.currentDownSpeed = downSpeed;
        this.currentUpSpeed = upSpeed;
        updateSessionTraffic(rxBytes, txBytes);
    }

    public synchronized void flushPendingTraffic() {
        if (pendingDeltaRx > 0 || pendingDeltaTx > 0) {
            long newRx = prefs.getLong(KEY_ALL_TIME_RX, 0L) + pendingDeltaRx;
            long newTx = prefs.getLong(KEY_ALL_TIME_TX, 0L) + pendingDeltaTx;
            prefs.edit()
                    .putLong(KEY_ALL_TIME_RX, newRx)
                    .putLong(KEY_ALL_TIME_TX, newTx)
                    .apply();
            pendingDeltaRx = 0;
            pendingDeltaTx = 0;
        }
        lastPersistTimeMs = System.currentTimeMillis();
    }

    public synchronized void stopSession() {
        this.sessionActive = false;
        this.currentDownSpeed = 0;
        this.currentUpSpeed = 0;
        flushPendingTraffic();
    }

    public synchronized boolean isSessionActive() { return sessionActive; }

    // ─── All-time statistics ─────────────────────────────────────────────────

    public synchronized long getAllTimeRxBytes() { return prefs.getLong(KEY_ALL_TIME_RX, 0L) + pendingDeltaRx; }
    public synchronized long getAllTimeTxBytes() { return prefs.getLong(KEY_ALL_TIME_TX, 0L) + pendingDeltaTx; }
    public synchronized long getAllTimeTotalBytes() { return getAllTimeRxBytes() + getAllTimeTxBytes(); }
    public synchronized int getConnectionCount() { return prefs.getInt(KEY_CONNECT_COUNT, 0); }

    public synchronized void resetAllTimeStats() {
        pendingDeltaRx = 0;
        pendingDeltaTx = 0;
        lastPersistTimeMs = System.currentTimeMillis();
        prefs.edit()
                .putLong(KEY_ALL_TIME_RX, 0L)
                .putLong(KEY_ALL_TIME_TX, 0L)
                .putInt(KEY_CONNECT_COUNT, 0)
                .apply();
        LogManager.log("TrafficStatsManager", "历史所有流量统计已彻底重置为零");
    }

    // ─── Session getters ─────────────────────────────────────────────────────

    public synchronized long getSessionRxBytes() { return sessionRxBytes; }
    public synchronized long getSessionTxBytes() { return sessionTxBytes; }
    public synchronized String getOutboundIp() { return outboundIp; }
    public synchronized void setOutboundIp(String ip) { this.outboundIp = ip; }
    public synchronized String getRemoteEndpoint() { return remoteEndpoint; }
    public synchronized String getProtocolChannel() { return protocolChannel; }
    public synchronized String getVirtualTunIp() { return virtualTunIp; }
    public synchronized double getCurrentDownSpeed() { return currentDownSpeed; }
    public synchronized double getCurrentUpSpeed() { return currentUpSpeed; }

    public synchronized String getSessionDurationFormatted() {
        if (!sessionActive || sessionStartTime <= 0) return "00:00:00";
        long d = (System.currentTimeMillis() - sessionStartTime) / 1000;
        return String.format(Locale.US, "%02d:%02d:%02d", d / 3600, (d % 3600) / 60, d % 60);
    }

    public static String formatBytesStatic(long bytes) {
        return formatBytes(bytes);
    }

    public static String formatBytes(long bytes) {
        if (bytes < 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        double value = bytes;
        String[] names = {"KB", "MB", "GB", "TB", "PB", "EB"};
        for (int i = 0; i < names.length; i++) {
            value /= 1024.0;
            if (value < 1024) {
                return String.format(Locale.US, "%.2f %s", value, names[i]);
            }
        }
        return String.format(Locale.US, "%.2f EB", value);
    }
}
