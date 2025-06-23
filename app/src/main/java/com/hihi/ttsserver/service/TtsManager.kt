package com.hihi.ttsserver.service

import android.content.Context
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.util.Log
import com.hihi.ttsserver.utils.ConfigManager
import com.hihi.ttsserver.data.entity.TtsConfig
import com.hihi.ttsserver.model.tts.BaseTTS
import com.hihi.ttsserver.model.tts.MsTTS
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.collect
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.media.MediaDataSource
import java.io.ByteArrayOutputStream
import java.io.File
import com.hihi.ttsserver.R
import java.nio.ByteBuffer
import android.media.AudioAttributes
import android.media.AudioTrack
import com.google.gson.Gson
import kotlin.math.min
import android.os.Build
import androidx.annotation.RequiresApi
import com.hihi.ttsserver.utils.LogCollector
import com.hihi.ttsserver.service.NotificationHelper

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TtsManager(private val context: Context) {
    companion object {
        private const val TAG = "TtsManager"
        private const val CLOSE_1006_PREFIX = "websocket: close 1006"
        private const val requestInterval = 100L
        private const val TIMEOUT_US = 10000L
        
        // 系统UID
        private const val SYSTEM_UID = 1000
        private const val ROOT_UID = 0
    }

    // 确保 configManager 是公共的
    public val configManager = ConfigManager.getInstance(context)

    // 是否正在合成
    var isSynthesizing = false
        private set

    // 协程作用域
    private val scope = CoroutineScope(Job() + Dispatchers.IO)

    // 当前TTS配置列表
    private var currentConfigs: List<TtsConfig> = emptyList()

    // 音频生产者
    private var producer: ReceiveChannel<ChannelData>? = null

    // 音频播放器
    private var mediaPlayer: MediaPlayer? = null

    // 音频缓存
    private var shortText : String? = null
    private var lastAudioData: ByteArray? = null
    private var isProcessingNext = false
    private val processScope = CoroutineScope(Job() + Dispatchers.IO)
    
    // 请求来源记录
    private var lastRequestUid: Int? = null
    private var lastRequestPackage: String? = null
    private var isSystemRequest = false

    private val gson = Gson()

    init {
        // 在初始化时加载配置
        loadConfig()
    }

    /**
     * 加载配置
     */
    fun loadConfig() {
        currentConfigs = configManager.getEnabledConfigs()
        LogCollector.log(Log.DEBUG, TAG, "TTS配置已加载: ${currentConfigs.size} 个配置")
    }

    /**
     * 停止合成及播放
     */
    fun stop() {
        isSynthesizing = false
        isProcessingNext = false
        lastAudioData = null
        lastRequestUid = null
        lastRequestPackage = null
        isSystemRequest = false
        producer?.cancel()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    /**
     * 销毁时调用
     */
    fun destroy() {
        processScope.cancel()
        scope.cancel()
        if (isSynthesizing) stop()
        // 清理资源，如果 BaseTTS 有相应方法
        createTts(currentConfigs).release()
    }

    /**
     * 判断是否是系统请求
     */
    private fun isSystemUid(uid: Int?): Boolean {
        if (uid == null) return false
        return uid < 10000 || uid == SYSTEM_UID || uid == ROOT_UID
    }

    /**
     * 获取请求来源信息
     */
    private fun getRequestSourceInfo(uid: Int?): String {
        if (uid == null) return "unknown"
        
        return when {
            uid == SYSTEM_UID -> "system"
            uid == ROOT_UID -> "root"
            uid < 10000 -> "system_service"
            else -> {
                try {
                    val packageName = context.packageManager.getNameForUid(uid)
                    packageName ?: "uid_$uid"
                } catch (e: Exception) {
                    "uid_$uid"
                }
            }
        }
    }

    /**
     * 开始转语音
     * @param text 要合成的文本
     * @param request 合成请求参数（目前未使用，保留用于未来扩展）
     * @param callback 合成回调
     */
    suspend fun synthesizeText(
        text: String,
        request: SynthesisRequest?,
        callback: SynthesisCallback?
    ) {
        isSynthesizing = true
        
        try {
            // 检查请求来源
            val uid = request?.callerUid
            val sourceInfo = getRequestSourceInfo(uid)
            isSystemRequest = isSystemUid(uid)
            lastRequestUid = uid
            lastRequestPackage = sourceInfo
            
            shortText = ellipsizeMiddle(text)
            var startTime = System.currentTimeMillis()
            LogCollector.log(Log.DEBUG, TAG, "收到合成请求,$sourceInfo:$shortText")
            
            callback?.start(24000, AudioFormat.ENCODING_PCM_16BIT, 1)
            if (isProcessingNext && lastAudioData == null) {
                // 等待处理完成
                while (isProcessingNext) {
                    delay(100)
                }
            }
            
            // 返回上一次缓存的音频数据
            val currentAudio = lastAudioData
            if (currentAudio == null) {
                if (!isProcessingNext && !isSystemRequest) {
                    LogCollector.log(Log.DEBUG, TAG, "首次合成，返回空音频")
                    // 返回一个空的音频数据
                    writeToCallback(callback, ByteArray(0))
                } 
            }

            // 异步处理下一段音频
            if (!isProcessingNext) {
                isProcessingNext = true
                processScope.launch {
                    try {
                        val tts = createTts(currentConfigs)
                        producer = audioStreamProducer(text, tts)
                        producer?.let { channel ->
                            for (data in channel) {
                                if (!isSynthesizing && !isSystemRequest) break
                                
                                if (data.audio == null) {
                                    LogCollector.log(Log.DEBUG, TAG, "收到合成的空音频数据")
                                    continue
                                }
                                
                                val pcmData = withContext(Dispatchers.IO) {
                                    convertMp3ToPcm(data.audio)
                                }
                                var useTime = String.format("%.1f", (System.currentTimeMillis() - startTime)/1000.0)
                                LogCollector.log(Log.DEBUG, TAG, "完成合成请求用时 $useTime 秒, $shortText")
                                // 缓存音频数据
                                lastAudioData = pcmData
                                
                            }
                        }
                    } finally {
                        isProcessingNext = false
                    }
                }

                if (isSystemRequest) {
                    LogCollector.log(Log.DEBUG, TAG, "系统请求直接返回音频数据")
                    while (isProcessingNext) {
                        delay(100)
                    }
                    NotificationHelper.updateNotification(context.getString(R.string.tts_state_playing), shortText)
                    lastAudioData?.let { audio ->
                        writeToCallback(callback, audio)
                    }
                    lastAudioData = null
                } else {
                    if (currentAudio != null) {
                        lastAudioData = null
                        // 更新通知显示正在合成
                        NotificationHelper.updateNotification(context.getString(R.string.tts_state_playing), shortText)
                        writeToCallback(callback, currentAudio)
                        LogCollector.log(Log.DEBUG, TAG, "应用请求返回缓存音频数据")
                    }
                }
            }
        } finally {
            isSynthesizing = false
        }
    }

    /**
     * 创建TTS实例
     */
    private fun createTts(configs: List<TtsConfig>): BaseTTS {
        val tts = MsTTS()
        // 将配置转换为JSON字符串
        val configJson = gson.toJson(configs)
        tts.updateConfig(configJson)
        return tts
    }

    /**
     * 音频流生产者
     */
    private fun audioStreamProducer(
        text: String,
        tts: BaseTTS
    ): ReceiveChannel<ChannelData> = scope.produce(capacity = Channel.BUFFERED) {
        try {
            tts.getAudioStream(text) { audio ->
                if (!isActive) {
                    LogCollector.log(Log.DEBUG, TAG, "生产者已取消，停止发送数据")
                    return@getAudioStream
                }
                trySendBlocking(ChannelData(audio = audio))
            }
        } catch (e: CancellationException) {
            LogCollector.log(Log.WARN, TAG, "音频流生产者被取消: ${e.message}")
        } catch (e: Exception) {
            LogCollector.log(Log.ERROR, TAG, "音频流生产者出错: ${e.message}")
        }
    }

    /**
     * 写入音频数据到回调
     */
    private fun writeToCallback(callback: SynthesisCallback?, audio: ByteArray) {
        try {
            val maxBufferSize = callback?.maxBufferSize ?: return
            var offset = 0
            
            while (offset < audio.size && isSynthesizing) {
                val bytesToWrite = maxBufferSize.coerceAtMost(audio.size - offset)
                callback.audioAvailable(audio, offset, bytesToWrite)
                offset += bytesToWrite
            }
        } catch (e: Exception) {
            LogCollector.log(Log.ERROR, TAG, "写入音频数据失败: ${e.message}")
            throw e
        }
    }

    /**
     * 将MP3转换为PCM
     */
    private fun convertMp3ToPcm(mp3Data: ByteArray): ByteArray {
        try {
            val extractor = MediaExtractor()
            val dataSource = ByteArrayMediaDataSource(mp3Data)
            extractor.setDataSource(dataSource)
            return decodeAudioData(extractor)
        } catch (e: Exception) {
            LogCollector.log(Log.ERROR, TAG, "MP3转PCM失败: ${e.message}")
            throw e
        }
    }

    /**
     * 解码音频数据
     */
    private fun decodeAudioData(extractor: MediaExtractor): ByteArray {
        // 选择音频轨道
        val audioTrackIndex = (0 until extractor.trackCount)
            .find { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            ?: throw IllegalStateException("未找到音频轨道")

        val format = extractor.getTrackFormat(audioTrackIndex)
        extractor.selectTrack(audioTrackIndex)

        // 创建解码器
        val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        decoder.configure(format, null, null, 0)
        decoder.start()

        // 解码数据
        val bufferInfo = MediaCodec.BufferInfo()
        val outputStream = ByteArrayOutputStream()

        var isEOS = false
        while (!isEOS) {
            val inputBufferId = decoder.dequeueInputBuffer(TIMEOUT_US)
            if (inputBufferId >= 0) {
                val inputBuffer = decoder.getInputBuffer(inputBufferId)
                val sampleSize = extractor.readSampleData(inputBuffer!!, 0)

                if (sampleSize < 0) {
                    decoder.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    isEOS = true
                } else {
                    decoder.queueInputBuffer(inputBufferId, 0, sampleSize, extractor.sampleTime, 0)
                    extractor.advance()
                }
            }

            val outputBufferId = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            if (outputBufferId >= 0) {
                val outputBuffer = decoder.getOutputBuffer(outputBufferId)
                val chunk = ByteArray(bufferInfo.size)
                outputBuffer?.get(chunk)
                outputStream.write(chunk)
                decoder.releaseOutputBuffer(outputBufferId, false)
            }
        }

        // 清理资源
        decoder.stop()
        decoder.release()
        extractor.release()

        return outputStream.toByteArray()
    }

    private class ByteArrayMediaDataSource(private val data: ByteArray) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= data.size) return -1
            val bytesToRead = minOf(size, data.size - position.toInt())
            System.arraycopy(data, position.toInt(), buffer, offset, bytesToRead)
            return bytesToRead
        }

        override fun getSize(): Long = data.size.toLong()
        override fun close() {}
    }

    fun ellipsizeMiddle(text: String, maxLength: Int = 30): String {
        if (text.length <= maxLength) return text
        return text.take(maxLength-5) + "......" + text.takeLast(5)
    }

    /**
     * 音频数据通道
     */
    data class ChannelData(
        val audio: ByteArray? = null
    )
} 