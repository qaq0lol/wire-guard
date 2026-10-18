package com.proxy.wireopen.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.SystemProxyStatus
import com.proxy.wireopen.BuildConfig
import com.proxy.wireopen.R
import com.proxy.wireopen.model.ConnectionState
import com.proxy.wireopen.model.ProxyProfile
import com.proxy.wireopen.util.LogManager
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs two VPN endpoints inside one sing-box process and one Android TUN.
 */
class DoubleHopVpnService : VpnService(), CommandServerHandler {
    companion object {
        const val ACTION_START = "com.proxy.wireopen.DOUBLE_HOP_START"
        const val ACTION_STOP = "com.proxy.wireopen.DOUBLE_HOP_STOP"
        const val EXTRA_CONFIG = "extra_double_hop_config"
        const val EXTRA_FIRST_NAME = "extra_double_hop_first_name"
        const val EXTRA_SECOND_NAME = "extra_double_hop_second_name"

        private const val CHANNEL_ID = "wireopen_double_hop_channel"
        private const val NOTIFICATION_ID = 1002
        @Volatile private var libboxInitialized = false
        @Volatile private var activeTun: ParcelFileDescriptor? = null

        internal fun setTunDescriptor(descriptor: ParcelFileDescriptor) {
            activeTun?.let { runCatching { it.close() } }
            activeTun = descriptor
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val stopOnce = AtomicBoolean(false)
    private var commandServer: CommandServer? = null
    private var configText: String? = null
    private var currentProfile: ProxyProfile? = null
    private var firstName: String = "第一跳"
    private var secondName: String = "第二跳"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTunnel()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val content = intent.getStringExtra(EXTRA_CONFIG)
                if (content.isNullOrBlank()) {
                    fail("双跳配置为空")
                    return START_NOT_STICKY
                }
                configText = content
                firstName = intent.getStringExtra(EXTRA_FIRST_NAME) ?: "第一跳"
                secondName = intent.getStringExtra(EXTRA_SECOND_NAME) ?: "第二跳"
                currentProfile = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getSerializableExtra(UnifiedVpnService.EXTRA_PROFILE, ProxyProfile::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    (intent.getSerializableExtra(UnifiedVpnService.EXTRA_PROFILE) as? ProxyProfile)
                }
                stopOnce.set(false)
                UnifiedVpnService.publishDoubleHopState(this, ConnectionState.CONNECTING, null, currentProfile)
                startForegroundCompat()
                Thread({ startCore(content) }, "WireOpen-DoubleHopStart").start()
            }
        }
        return START_NOT_STICKY
    }

    private fun startCore(content: String) {
        try {
            initializeLibbox()
            val server = CommandServer(this, DoubleHopPlatform(this))
            commandServer = server
            server.start()
            server.startOrReloadService(content, OverrideOptions())
            LogManager.info("DoubleHopVpnService", "双跳内核已启动：$firstName → $secondName")
            mainHandler.post {
                updateNotification("已连接：$firstName → $secondName")
                UnifiedVpnService.publishDoubleHopState(this, ConnectionState.CONNECTED, null, currentProfile)
            }
        } catch (e: Exception) {
            LogManager.error("DoubleHopVpnService", "双跳启动失败：" + (e.message ?: e.javaClass.simpleName))
            Log.e("DoubleHopVpnService", "Failed to start double-hop core", e)
            fail(e.message ?: "双跳内核启动失败")
        }
    }

    private fun initializeLibbox() {
        synchronized(DoubleHopVpnService::class.java) {
            if (libboxInitialized) return
            val working = getExternalFilesDir(null) ?: File(filesDir, "singbox-work")
            working.mkdirs()
            cacheDir.mkdirs()
            Libbox.setLocale(Locale.getDefault().toLanguageTag())
            val options = SetupOptions().apply {
                basePath = filesDir.absolutePath
                workingPath = working.absolutePath
                tempPath = cacheDir.absolutePath
                logMaxLines = 2000
                debug = BuildConfig.DEBUG
            }
            Libbox.setup(options)
            libboxInitialized = true
        }
    }

    private fun startForegroundCompat() {
        val stopIntent = Intent(this, DoubleHopVpnService::class.java).apply { action = ACTION_STOP }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val stopPendingIntent = PendingIntent.getService(this, NOTIFICATION_ID, stopIntent, flags)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("WireOpen 双跳连接")
            .setContentText("正在连接：$firstName → $secondName")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "断开", stopPendingIntent)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(message: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("WireOpen 双跳连接")
            .setContentText(message)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                0,
                "断开",
                PendingIntent.getService(
                    this,
                    NOTIFICATION_ID,
                    Intent(this, DoubleHopVpnService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0),
                ),
            )
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "双跳 VPN", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun fail(message: String) {
        LogManager.error("DoubleHopVpnService", message)
        mainHandler.post {
            UnifiedVpnService.publishDoubleHopState(this, ConnectionState.ERROR, message, currentProfile)
            stopTunnel()
        }
    }

    private fun stopTunnel(fromCommandServer: Boolean = false) {
        if (!stopOnce.compareAndSet(false, true) && !fromCommandServer) return
        val server = commandServer
        commandServer = null
        Thread({
            try {
                if (server != null) {
                    if (!fromCommandServer) runCatching { server.closeService() }
                    runCatching { server.close() }
                }
                activeTun?.let {
                    runCatching { it.close() }
                    activeTun = null
                }
            } finally {
                mainHandler.post {
                    UnifiedVpnService.publishDoubleHopState(this, ConnectionState.DISCONNECTED, null, null)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }, "WireOpen-DoubleHopStop").start()
    }

    override fun serviceStop() {
        mainHandler.post { stopTunnel(fromCommandServer = true) }
    }

    override fun serviceReload() {
        val content = configText ?: return
        Thread({
            try {
                commandServer?.startOrReloadService(content, OverrideOptions())
            } catch (e: Exception) {
                fail(e.message ?: "双跳重载失败")
            }
        }, "WireOpen-DoubleHopReload").start()
    }

    override fun getSystemProxyStatus(): SystemProxyStatus =
        SystemProxyStatus().apply {
            available = false
            enabled = false
        }

    override fun setSystemProxyEnabled(isEnabled: Boolean) {}

    override fun triggerNativeCrash() {
        Log.w("DoubleHopVpnService", "Native crash trigger ignored")
    }

    override fun writeDebugMessage(message: String?) {
        LogManager.debug("sing-box", message ?: "")
    }

    override fun connectSSHAgent(): Int = -1

    override fun onRevoke() {
        stopTunnel()
        super.onRevoke()
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    override fun onDestroy() {
        if (commandServer != null || activeTun != null) stopTunnel()
        super.onDestroy()
    }
}
