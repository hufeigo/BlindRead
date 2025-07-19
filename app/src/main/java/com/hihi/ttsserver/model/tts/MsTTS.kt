package com.hihi.ttsserver.model.tts

import android.util.Log
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.chaquo.python.PyObject
import com.google.gson.Gson

class MsTTS : BaseTTS {

    companion object {
        private const val TAG = "MsTTS"
    }

    private lateinit var python: Python
    private lateinit var ttsModule: PyObject
    private val gson = Gson()

    init {
        try {
            python = Python.getInstance()
            ttsModule = python.getModule("tts")
        } catch (e: PyException) {
            Log.e(TAG, "Python初始化失败", e)
        }
    }

    private var voice: String = "zh-CN-XiaoxiaoNeural"
    private var style: String = "general"
    private var rate: Float = 1.0f
    private var volume: Float = 1.0f
    private var pitch: Float = 1.0f
    private var styleIntensity: Float = 1.0f
    private var role: String? = null
    private var audioFormat: String = "audio-16khz-32kbitrate-mono-mp3"

    override fun setVoice(voice: String) {
        this.voice = voice
    }

    override fun setStyle(style: String) {
        this.style = style
    }

    override fun setRate(rate: Float) {
        this.rate = rate
    }

    override fun setVolume(volume: Float) {
        this.volume = volume
    }

    override fun setPitch(pitch: Float) {
        this.pitch = pitch
    }

    fun setStyleIntensity(styleIntensity: Float) {
        this.styleIntensity = styleIntensity
    }

    fun setRole(role: String) {
        this.role = role
    }

    fun setAudioFormat(audioFormat: String) {
        this.audioFormat = audioFormat
    }

    override fun updateConfig(configJson: String) {
        try {
            ttsModule.callAttr("set_voice_config", configJson)
            Log.d(TAG, "TTS配置更新成功")
        } catch (e: PyException) {
            Log.e(TAG, "Python TTS配置更新失败", e)
        } catch (e: Exception) {
            Log.e(TAG, "TTS配置更新失败", e)
        }
    }

    override fun getAudioStream(text: String, onRead: (ByteArray?) -> Unit) {
        try {
            val result = ttsModule.callAttr("synthesize_text", text)
            if (result == null) {
                Log.e(TAG, "Python TTS合成返回null")
                onRead(null)
                return
            }
            val audioBytes = result.toJava(ByteArray::class.java)
            onRead(audioBytes)
        } catch (e: PyException) {
            Log.e(TAG, "Python TTS合成失败", e)
            onRead(null)
        } catch (e: Exception) {
            Log.e(TAG, "TTS合成失败", e)
            onRead(null)
        }
    }

    override fun stop() {
        // 在这里实现停止逻辑，如果Python脚本支持停止合成
    }

    override fun release() {
        // 在这里实现资源清理逻辑
    }
} 