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
    }

    private val mTtsManager: TtsManager by lazy { TtsManager(this) }
    private val mReceiver: MyReceiver by lazy { MyReceiver() }
    private val mScope = CoroutineScope(Job())

    // WIFI 锁
    private val mWifiLock by lazy {
        val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        wm.createWifiLock(WifiManager.WIFI_MODE_FULL, "zz_tts_service:wifi")
    }

    // 唤醒锁
    private val mWakeLock by lazy {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
            "zz_tts_service:wake_lock"
        )
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
        mWakeLock.acquire(60 * 20 * 1000)
        mWifiLock.acquire()
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
        mWakeLock.release()
        mWifiLock.release()
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
        val text = request?.charSequenceText.toString().trim()
        if (text.isBlank()) {
            Log.d(TAG, "文本为空，跳过合成")
            callback?.done()
            return
        }
        Log.d(TAG, "开始合成文本: $text")

        // 获取系统语速和音调
        val speechRate = request?.speechRate ?: 100.0f
        val pitch = request?.pitch ?: 100.0f
        Log.d(TAG, "系统语速: $speechRate, 音调: $pitch")

        reNewWakeLock()
        
        val currentConfigs = mTtsManager.configManager.getEnabledConfigs()
        if (currentConfigs.isEmpty()) {
            Log.e(TAG, "No enabled TTS config found.")
            // 在通知中显示提示
            NotificationHelper.updateNotification("", "未找到启用的TTS配置")
            callback?.error(TextToSpeech.ERROR_SYNTHESIS)
            callback?.done()
            return
        }

        // 更新TTS配置中的语速和音调
        /* 
        currentConfigs.forEach { config ->
            config.rate = (config.rate * (speechRate.toFloat() / 100f))*1
            config.pitch = (config.pitch * (pitch.toFloat() / 100f))*1
            //mTtsManager.configManager.updateConfig(config)
            Log.d(TAG, "系统*配置语速: $speechRate, 音调: $pitch")
        }
        */
        mCurrentText = text

        runBlocking { 
            mTtsManager.synthesizeText(text, request, callback)
        }

        callback?.done()
    }

    private fun reNewWakeLock() {
        if (!mWakeLock.isHeld) {
            mWakeLock.acquire(60 * 20 * 1000)
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