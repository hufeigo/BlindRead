package com.hihi.ttsserver.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log

class GetSampleText : Activity() {
    companion object {
        private const val TAG = "GetSampleText"
        private const val EXTRA_PARAM_LANGUAGE = "language"
        private const val EXTRA_PARAM_COUNTRY = "country"
        private const val EXTRA_PARAM_VARIANT = "variant"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 获取请求的语言
        val language = intent.getStringExtra(EXTRA_PARAM_LANGUAGE)
        val country = intent.getStringExtra(EXTRA_PARAM_COUNTRY)
        val variant = intent.getStringExtra(EXTRA_PARAM_VARIANT)

        // 设置示例文本
        val sampleText = when {
            language?.startsWith("zh") == true -> "这是一段中文示例文本:\"用于测试语音合成效果。\""
            else -> "This is a sample text for testing text-to-speech."
        }

        // 设置结果并结束Activity
        setResult(TextToSpeech.LANG_AVAILABLE, Intent().apply {
            putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, sampleText)
        })
        finish()
    }
} 