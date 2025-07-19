package com.hihi.ttsserver.model.tts

import kotlinx.coroutines.flow.Flow

interface BaseTTS {
    fun setVoice(voice: String)
    fun setStyle(style: String)
    fun setRate(rate: Float)
    fun setVolume(volume: Float)
    fun setPitch(pitch: Float)
    fun updateConfig(configJson: String)
    fun getAudioStream(text: String, onRead: (ByteArray?) -> Unit)
    fun stop()
    fun release()
} 