package com.hihi.ttsserver.data.entity

import com.hihi.ttsserver.model.tts.TtsVoice

data class TtsConfig(
    val id: Int = 0,
    val name: String = "",
    val voice: TtsVoice,
    val text: String = "",
    val enabled: Boolean = false,
    var rate: Float = 0.0f,
    var volume: Float = 0.0f,
    var pitch: Float = 0.0f,
    val styleIntensity: Float = 1.0f,
    val audioFormat: String = "audio-16khz-32kbitrate-mono-mp3",
    val scope: String = "旁白",
    val apiName: String = "Edge"
) 