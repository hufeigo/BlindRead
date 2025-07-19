package com.hihi.ttsserver.data.entity

import com.google.gson.annotations.SerializedName

data class AppSettingsConfig(
    @SerializedName("splitLongSentences") val splitLongSentences: Boolean = false,
    @SerializedName("multiLanguageEnabled") val multiLanguageEnabled: Boolean = false,
    @SerializedName("multipleSelectionForSameReadingTarget") val multipleSelectionForSameReadingTarget: Boolean = false,
    @SerializedName("multipleSelectionForGrouping") val multipleSelectionForGrouping: Boolean = false,
    @SerializedName("playAudioInApp") val playAudioInApp: Boolean = false,
    @SerializedName("minDialogueChineseCharacters") val minDialogueChineseCharacters: Int = 0,
    @SerializedName("requestTimeout") val requestTimeout: Int = 5000, // 默认5秒
    @SerializedName("cacheAudioBookAudio") val cacheAudioBookAudio: Boolean = true // 默认缓存听书音频
) 