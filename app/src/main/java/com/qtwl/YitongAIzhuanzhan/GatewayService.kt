package com.qtwl.YitongAIzhuanzhan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class GatewayService : Service() {

    companion object {
        const val CHANNEL_ID = "qitong_gateway"
        const val NOTIF_ID = 1001
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        @Volatile var isRunning = false
            private set

        fun start(ctx: Context) {
            val intent = Intent(ctx, GatewayService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent)
            else ctx.startService(intent)
        }
        fun stop(ctx: Context) { ctx.startService(Intent(ctx, GatewayService::class.java).apply { action = ACTION_STOP }) }
    }

    private var gateway: GatewayServer? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "綦桐网关", NotificationManager.IMPORTANCE_LOW).apply {
                description = "网页AI网关运行状态"
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startGateway()
            ACTION_STOP -> {
                gateway?.stop()
                gateway = null
                isRunning = false
                stopForeground(true)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startGateway() {
        val port = GatewayPrefs.getPort(this).toIntOrNull() ?: 7773
        val notification = buildNotif("綦桐AI转站", "网关运行中 · 端口 $port · 等待请求...")
        startForeground(NOTIF_ID, notification)

        if (gateway?.isRunning() == true) {
            isRunning = true
            updateNotif("綦桐AI转站", "网关已运行 · 端口 $port")
            return
        }

        gateway?.stop()
        gateway = GatewayServer(this, port).apply {
            onRequestReceived = { prompt -> updateNotif("处理中", "${prompt.take(30)}...") }
            onReplyReady = { reply -> updateNotif("綦桐AI转站", "回复完成 (${reply.length}字)") }
            onRequestFailed = { error -> updateNotif("綦桐AI转站", "请求失败: ${error.take(60)}") }
        }
        if (gateway?.startServer() == true) {
            isRunning = true
            updateNotif("綦桐AI转站", "网关运行中 · 端口 $port")
        } else {
            gateway = null
            updateNotif("綦桐AI转站", "网关启动失败 · 端口 $port")
            stopForeground(true)
            stopSelf()
        }
    }

    private fun buildNotif(title: String, text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title).setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pi).setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun updateNotif(title: String, text: String) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, buildNotif(title, text))
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        gateway?.stop()
        gateway = null
        super.onDestroy()
    }
}