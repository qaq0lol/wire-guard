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
        private const val STARTUP_TIMEOUT_MS = 30_000L
        private const val STOP_FORCE_CLOSE_DELAY_MS = 2_500L
        @Volatile private var libboxInitialized = false
        @Volatile private var activeTun: ParcelFileDescriptor? = null

        internal fun setTunDescriptor(descriptor: ParcelFileDescriptor) {
            activeTun?.let { runCatching { it.close() } }
            activeTun = descriptor
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val stopOnce = AtomicBoolean(false)
    private val cleanupStarted = AtomicBoolean(false)
    private val failurePosted = AtomicBoolean(false)
    private val startupGeneration = java.util.concurrent.atomic.AtomicInteger(0)
    @Volatile private var startupFinished = false
    @Volatile private var starting = false
    @Volatile private var commandServer: CommandServer? = null
    private var configText: String? = null

    private val startupTimeoutRunnable = Runnable {
        if (starting && !startupFinished && !stopOnce.get()) {
            fail("双跳启动超时（30 秒）。内核未完成启动，已触发自动清理，请检查节点连通性和配置。")
        }
    }
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
                LogManager.info("DoubleHopVpnService", "收到断开双跳请求")
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
                if (starting || (!stopOnce.get() &&
                            (commandServer != null ||
                             UnifiedVpnService.getCurrentState() == ConnectionState.CONNECTED))) {
                    LogManager.warn("DoubleHopVpnService", "忽略重复的双跳启动请求")
                    return START_NOT_STICKY
                }
                cleanupStarted.set(false)
                failurePosted.set(false)
                stopOnce.set(false)
                startupFinished = false
                starting = true
                val token = startupGeneration.incrementAndGet()
                UnifiedVpnService.publishDoubleHopState(this, ConnectionState.CONNECTING, null, currentProfile)
                LogManager.info("DoubleHopVpnService", "双跳启动流程开始：$firstName → $secondName")
                startForegroundCompat()
                mainHandler.removeCallbacks(startupTimeoutRunnable)
                mainHandler.postDelayed(startupTimeoutRunnable, STARTUP_TIMEOUT_MS)
                Thread({ startCore(content, token) }, "WireOpen-DoubleHopStart").start()
            }
        }
        return START_NOT_STICKY
    }

    private fun startCore(content: String, token: Int) {
        var server: CommandServer? = null
        try {
            LogManager.info("DoubleHopVpnService", "启动阶段 1/4：初始化 sing-box")
            initializeLibbox()
            if (!isStartupCurrent(token)) return

            LogManager.info("DoubleHopVpnService", "启动阶段 2/4：创建命令服务")
            server = CommandServer(this, DoubleHopPlatform(this))
            commandServer = server

            LogManager.info("DoubleHopVpnService", "启动阶段 3/4：启动命令服务")
            server.start()
            if (!isStartupCurrent(token)) {
                LogManager.warn("DoubleHopVpnService", "命令服务启动后发现连接已取消")
                return
            }

            LogManager.info("DoubleHopVpnService", "启动阶段 4/4：加载双跳配置并建立 TUN")
            server.startOrReloadService(content, OverrideOptions())

            if (!isStartupCurrent(token)) {
                LogManager.warn("DoubleHopVpnService", "启动返回时连接已取消，丢弃过期启动结果")
                return
            }

            startupFinished = true
            starting = false
            mainHandler.removeCallbacks(startupTimeoutRunnable)
            LogManager.info("DoubleHopVpnService", "双跳内核启动调用已返回：$firstName → $secondName")
            mainHandler.post {
                if (!isStartupCurrent(token)) return@post
                updateNotification("已连接：$firstName → $secondName")
                UnifiedVpnService.publishDoubleHopState(this, ConnectionState.CONNECTED, null, currentProfile)
            }
        } catch (e: Exception) {
            if (!isStartupCurrent(token)) {
                LogManager.warn("DoubleHopVpnService", "已取消的启动线程退出：" + (e.message ?: e.javaClass.simpleName))
                return
            }
            LogManager.error("DoubleHopVpnService", "双跳启动失败：" + (e.message ?: e.javaClass.simpleName))
            Log.e("DoubleHopVpnService", "Failed to start double-hop core", e)
            fail(e.message ?: "双跳内核启动失败")
        } catch (t: Throwable) {
            if (!isStartupCurrent(token)) return
            LogManager.error("DoubleHopVpnService", "双跳启动发生严重错误：" + (t.message ?: t.javaClass.simpleName))
            Log.e("DoubleHopVpnService", "Fatal error while starting double-hop core", t)
            fail(t.message ?: "双跳启动发生严重错误")
        }
    }

    private fun isStartupCurrent(token: Int): Boolean =
        token == startupGeneration.get() && !stopOnce.get()

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
                debug = true
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
        if (!failurePosted.compareAndSet(false, true)) return
        // Invalidate any startup worker that may return after the timeout.
        startupGeneration.incrementAndGet()
        LogManager.error("DoubleHopVpnService", message)
        Log.e("DoubleHopVpnService", message)
        mainHandler.post {
            if (stopOnce.get()) return@post
            startupFinished = true
            starting = false
            mainHandler.removeCallbacks(startupTimeoutRunnable)
            UnifiedVpnService.publishDoubleHopState(this, ConnectionState.ERROR, message, currentProfile)
            // Keep the error visible briefly, then release the VPN service even if the
            // native start call never returns.
            mainHandler.postDelayed({ stopTunnel() }, 1_500L)
        }
    }

    private fun stopTunnel(fromCommandServer: Boolean = false) {
        if (!cleanupStarted.compareAndSet(false, true)) return
        stopOnce.set(true)
        starting = false
        startupFinished = true
        startupGeneration.incrementAndGet()
        mainHandler.removeCallbacks(startupTimeoutRunnable)

        val server = commandServer
        commandServer = null
        val tun = activeTun
        activeTun = null

        // Drop the system VPN interface and publish the disconnected state before
        // calling libbox shutdown. Native close methods may block during a failed startup.
        if (tun != null) {
            runCatching { tun.close() }
                .onFailure { LogManager.warn("DoubleHopVpnService", "关闭 TUN 失败：" + (it.message ?: "未知错误")) }
        }
        LogManager.info("DoubleHopVpnService", "双跳断开请求已处理，正在清理内核")
        UnifiedVpnService.publishDoubleHopState(this, ConnectionState.DISCONNECTED, null, null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        if (server != null) {
            val shutdown = Thread({
                val closeThread = Thread({
                    try {
                        if (!fromCommandServer) runCatching { server.closeService() }
                    } finally {
                        runCatching { server.close() }
                    }
                }, "WireOpen-DoubleHopNativeClose")
                closeThread.start()
                try {
                    closeThread.join(STOP_FORCE_CLOSE_DELAY_MS)
                } catch (ignored: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                if (closeThread.isAlive) {
                    LogManager.warn("DoubleHopVpnService", "内核关闭超时，执行强制 close")
                    runCatching { server.close() }
                    closeThread.interrupt()
                }
            }, "WireOpen-DoubleHopStop")
            shutdown.start()
        }
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
        if (starting || commandServer != null || activeTun != null) stopTunnel()
        super.onDestroy()
    }
}
