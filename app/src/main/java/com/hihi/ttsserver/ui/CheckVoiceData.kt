package com.hihi.ttsserver.ui

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.appcompat.app.AppCompatActivity

class CheckVoiceData : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent().apply {
            putStringArrayListExtra(
                TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES,
                arrayListOf("zho")
            )
            putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, arrayListOf())
        }
        setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, intent)
        finish()
    }
}