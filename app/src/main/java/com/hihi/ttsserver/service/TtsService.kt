package com.hihi.ttsserver.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.media.AudioFormat
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.util.Log
import androidx.core.content.ContextCompat
import com.hihi.ttsserver.R
import com.hihi.ttsserver.ui.MainActivity
import kotlinx.coroutines.*
import androidx.core.app.NotificationCompat

class TtsService : TextToSpeechService() {
    companion object {
        private const val TAG = "TtsService"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_KILL_PROCESS = "com.hihi.ttsserver.action.KILL_PROCESS"
        private const val ACTION_NOTIFY_CANCEL = "com.hihi.ttsserver.action.NOTIFY_CANCEL"
        private const val ACTION_STOP = "com.hihi.ttsserver.action.STOP"
        const val ACTION_NOTIFY_CANCEL_OLD = "TTS_NOTIFY_CANCEL"
        const val ACTION_STOP_OLD = "com.hihi.ttsserver.action.STOP"
        private const val WAKE_LOCK_TIMEOUT = 5 * 60 * 1000L // 5分钟超时
        private const val WIFI_LOCK_TIMEOUT = 5 * 60 * 1000L // 5分钟超时
    }

    private val mTtsManager: TtsManager by lazy { TtsManager(this) }
    private val mReceiver: MyReceiver by lazy { MyReceiver() }
    private val mScope = CoroutineScope(Job())

    // 唤醒锁
    private val mWakeLock by lazy {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
            "zz_tts_service:wake_lock"
        )
    }

    // 添加超时释放的 Handler
    private val mHandler = Handler(Looper.getMainLooper())
    private var mWakeLockReleaseRunnable: Runnable? = null
    private var mWifiLockReleaseRunnable: Runnable? = null

    // WIFI 锁
    @Suppress("DEPRECATION")
    private val mWifiLock by lazy {
        val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        wm.createWifiLock(WifiManager.WIFI_MODE_FULL, "zz_tts_service:wifi")
    }

    override fun onCreate() {
        super.onCreate()
        // 初始化全局通知助手
        NotificationHelper.init(this, NOTIFICATION_ID)
        // 注册广播接收器
        IntentFilter().apply {
            addAction(ACTION_KILL_PROCESS)
            addAction(ACTION_NOTIFY_CANCEL)
            addAction(ACTION_STOP)
            registerReceiver(mReceiver, this, Context.RECEIVER_NOT_EXPORTED)
        }
        mTtsManager.loadConfig()
        // 启动前台服务
        val notification = NotificationHelper.createNotification(this, getString(R.string.tts_service_running), "")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mTtsManager.destroy()
        unregisterReceiver(mReceiver)
        releaseWakeLock()
        releaseWifiLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        return when {
            lang == "zho" || lang == "zh" -> TextToSpeech.LANG_AVAILABLE
            else -> TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun onGetLanguage(): Array<String> {
        return arrayOf("zho", "CN", "")
    }

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {

        return when {
            lang == "zho" || lang == "zh" -> TextToSpeech.LANG_AVAILABLE
            else -> TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun onStop() {
        Log.d(TAG, "onStop")
        mTtsManager.stop()
    }

    private var mCurrentText: String = ""

    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        if (request == null || callback == null) {
            return
        }
        val text = request.charSequenceText.toString().trim()
        if (text.isBlank()) {
            Log.d(TAG, "文本为空，跳过合成")
            return
        }
        Log.d(TAG, "收到请求文本: $text")

        // 获取系统语速和音调
        val speechRate = request.speechRate
        val pitch = request.pitch
        Log.d(TAG, "系统语速: $speechRate, 音调: $pitch")

        val currentConfigs = mTtsManager.configManager.getEnabledConfigs()
        if (currentConfigs.isEmpty()) {
            Log.e(TAG, "No enabled TTS config found.")
            // 在通知中显示提示
            NotificationHelper.updateNotification("", "未找到启用的TTS配置")
            callback.error()
            return
        }

        mCurrentText = text

        // 在合成开始时获取锁
        acquireWakeLock()
        acquireWifiLock()

        runBlocking { 
            mTtsManager.synthesizeText(text, request, callback)
        }

        // 合成完成后释放锁
        releaseWakeLock()
        releaseWifiLock()
    }

    /**
     * 获取唤醒锁
     */
    private fun acquireWakeLock() {
        if (!mWakeLock.isHeld) {
            mWakeLock.acquire(WAKE_LOCK_TIMEOUT)
            Log.d(TAG, "获取唤醒锁")
            
            // 设置超时自动释放
            mWakeLockReleaseRunnable = Runnable {
                if (mWakeLock.isHeld) {
                    mWakeLock.release()
                    Log.d(TAG, "唤醒锁超时自动释放")
                }
            }
            mHandler.postDelayed(mWakeLockReleaseRunnable!!, WAKE_LOCK_TIMEOUT)
        }
    }

    /**
     * 释放唤醒锁
     */
    private fun releaseWakeLock() {
        if (mWakeLock.isHeld) {
            mWakeLock.release()
            Log.d(TAG, "释放唤醒锁")
        }
        // 取消超时释放任务
        mWakeLockReleaseRunnable?.let {
            mHandler.removeCallbacks(it)
            mWakeLockReleaseRunnable = null
        }
    }

    /**
     * 获取 WiFi 锁
     */
    private fun acquireWifiLock() {
        if (!mWifiLock.isHeld) {
            mWifiLock.acquire()
            Log.d(TAG, "获取 WiFi 锁")
            
            // 设置超时自动释放
            mWifiLockReleaseRunnable = Runnable {
                if (mWifiLock.isHeld) {
                    mWifiLock.release()
                    Log.d(TAG, "WiFi 锁超时自动释放")
                }
            }
            mHandler.postDelayed(mWifiLockReleaseRunnable!!, WIFI_LOCK_TIMEOUT)
        }
    }

    /**
     * 释放 WiFi 锁
     */
    private fun releaseWifiLock() {
        if (mWifiLock.isHeld) {
            mWifiLock.release()
            Log.d(TAG, "释放 WiFi 锁")
        }
        // 取消超时释放任务
        mWifiLockReleaseRunnable?.let {
            mHandler.removeCallbacks(it)
            mWifiLockReleaseRunnable = null
        }
    }

    @Suppress("DEPRECATION")
    class MyReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_KILL_PROCESS -> {
                    context.stopService(Intent(context, TtsService::class.java))
                }
                ACTION_NOTIFY_CANCEL -> {
                    context.stopService(Intent(context, TtsService::class.java))
                }
                ACTION_STOP -> {
                    context.stopService(Intent(context, TtsService::class.java))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra("text") ?: ""
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, NotificationHelper.createNotification(this, getString(R.string.tts_service_running), text), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, NotificationHelper.createNotification(this, getString(R.string.tts_service_running), text))
        }
        return START_NOT_STICKY
    }

    private fun stopTts() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
} 