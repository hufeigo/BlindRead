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
import android.media.AudioFormat
import com.hihi.ttsserver.R
import com.google.gson.Gson
import kotlin.math.min
import com.hihi.ttsserver.utils.LogCollector
import com.hihi.ttsserver.service.NotificationHelper
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlin.io.path.createTempFile
import kotlinx.coroutines.TimeoutCancellationException

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TtsManager(private val context: Context) {
    companion object {
        private const val TAG = "TtsManager"
        private const val PROCESSING_TIMEOUT_MS = 30000L // 30秒超时
        private const val WAIT_INTERVAL_MS = 50L // 减少等待间隔
        // 系统UID
        private const val SYSTEM_UID = 1000
        private const val ROOT_UID = 0
    }

    // 确保 configManager 是公共的
    public val configManager = ConfigManager.getInstance(context)

    // 是否正在合成
    @Volatile
    var isSynthesizing = false

    // 统一协程作用域管理
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
        LogCollector.log(Log.ERROR, TAG, "主协程异常: ${e.message}")
    })

    // 当前TTS配置列表
    private var currentConfigs: List<TtsConfig> = emptyList()

    // TTS单实例持有
    private var tts: BaseTTS? = null
    
    // 是否系统请求
    private var isSystemRequest = false

    private val gson = Gson()

    // 新增TtsRequest数据类
    data class TtsRequest(
        var sourceInfo: String,
        var isSystemRequest: Boolean = false,
        val text: String,
        var audio: ByteArray? = null,
        var actualSampleRate: Int = 24000,
        var isCompleted: Boolean = false,
        var startTime: Long = 0,
        var useTime: String = "",
        var shortText: String? = null
    )

    // 用于保存上一次和本次请求
    private var lastRequest: TtsRequest? = null
    private var currentRequest: TtsRequest? = null

    // 1. 配置缓存优化
    private var appSettingsConfig: com.hihi.ttsserver.data.entity.AppSettingsConfig = getAppSettingsConfig()
    fun reloadAppSettingsConfig() {
        appSettingsConfig = getAppSettingsConfig()
    }

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
        isSystemRequest = false
    }

    /**
     * 销毁时调用
     */
    fun destroy() {
        mainScope.cancel()
        if (isSynthesizing) stop()
        // 释放TTS单实例
        releaseTts()
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
                    packageName ?: "uid$uid"
                } catch (e: Exception) {
                    "uid_$uid"
                }
            }
        }
    }

    /**
     * 等待处理完成的优化方法 - 替换忙等待
     */
    private suspend fun waitForProcessing() {
        while (isSynthesizing) {
            delay(WAIT_INTERVAL_MS)
            yield() // 让出CPU时间片
        }
    }

    /**
     * 获取TTS单实例
     */
    private fun getTts(): BaseTTS {
        if (tts == null) {
            tts = createTts(currentConfigs)
        }
        return tts!!
    }

    /**
     * 释放TTS单实例
     */
    private fun releaseTts() {
        tts?.release()
        tts = null
    }

    // 获取全局 AppSettingsConfig
    private fun getAppSettingsConfig(): com.hihi.ttsserver.data.entity.AppSettingsConfig {
        return try {
            configManager.loadAppSettingsConfig()
        } catch (e: Exception) {
            LogCollector.log(Log.ERROR, TAG, "加载AppSettingsConfig失败: ${e.message}")
            com.hihi.ttsserver.data.entity.AppSettingsConfig()
        }
    }

    // 修改 getAudioStreamSuspend 使用缓存
    private suspend fun getAudioStreamSuspend(text: String, tts: BaseTTS): ByteArray? {
        val timeoutMs = appSettingsConfig.requestTimeout.takeIf { it > 0 } ?: 10000
        val maxRetry = 3

        suspend fun tryGetAudioStream(): ByteArray? = withTimeout(timeoutMs.toLong()) {
            suspendCancellableCoroutine { cont ->
                try {
                    tts.getAudioStream(text) { audio ->
                        cont.resume(audio, null)
                    }
                } catch (e: Exception) {
                    LogCollector.log(Log.ERROR, TAG, "合成音频异常: ${e.message}")
                    cont.resume(null, null)
                }
            }
        }

        repeat(maxRetry) { attempt ->
            try {
                return tryGetAudioStream()
            } catch (e: TimeoutCancellationException) {
                var shorText = ellipsizeMiddle(text)
                LogCollector.log(Log.ERROR, TAG, "合成音频超时(${timeoutMs}ms) 第${attempt + 1}次: $shorText")
                kotlinx.coroutines.delay(200L * (attempt + 1)) // 指数退避
            } catch (e: Exception) {
                LogCollector.log(Log.ERROR, TAG, "合成音频失败(第${attempt + 1}次): ${e.message}")
                kotlinx.coroutines.delay(200L * (attempt + 1))
            }
        }
        LogCollector.log(Log.ERROR, TAG, "合成音频重试${maxRetry}次全部失败: $text")
        return null
    }

    suspend fun synthesizeText(
        text: String,
        request: SynthesisRequest?,
        callback: SynthesisCallback?
    ) {
        // 1. 判断请求来源
        val uid = request?.callerUid
        val sourceInfo = getRequestSourceInfo(uid)
        isSystemRequest = isSystemUid(uid)
        val shortText = ellipsizeMiddle(text)
        LogCollector.log(Log.DEBUG, TAG, "收到请求,$sourceInfo:$shortText")
        Log.d(TAG, "收到请求,uid=$uid,source=$sourceInfo,text= $shortText")

        // 2. 等待上一次处理完成
        if (isSynthesizing) {
            waitForProcessing()
        }
        lastRequest = currentRequest
        currentRequest = null

        // 3、4. 启动新合成流程和音频流处理并缓存（放入协程中异步处理）
        isSynthesizing = true
        currentRequest = TtsRequest(sourceInfo = sourceInfo, text = text, startTime = System.currentTimeMillis(), shortText = shortText, isSystemRequest = isSystemRequest)
        val job = mainScope.launch {
            try {
                val tts = getTts()
                val audio = getAudioStreamSuspend(text, tts)
                
                if (audio != null) {
                    // 直接用 MediaCodec 解码
                    val pcmData = withContext(Dispatchers.Default) {
                        convertMp3ToPcmWithMediaCodec(audio)
                    }
                    currentRequest?.audio = pcmData
                    currentRequest?.actualSampleRate = 24000
                    currentRequest?.isCompleted = true
                }
                val useTime = String.format("%.1f", (System.currentTimeMillis() - (currentRequest?.startTime ?: System.currentTimeMillis()))/1000.0)
                currentRequest?.useTime = useTime
                LogCollector.log(Log.DEBUG, TAG, "完成合成 ${(audio?.size ?: 0) / 1024} KB, 用时 $useTime 秒, ${currentRequest?.shortText}")
                Log.d(TAG, "完成合成 ${(audio?.size ?: 0) / 1024} KB,用时 $useTime 秒, ${currentRequest?.shortText}")
            } catch (e: Exception) {
                LogCollector.log(Log.ERROR, TAG, "合成出错: ${e.message}")
            } finally {
                isSynthesizing = false
            }
        }
        

        // 5. 音频流回调与通知
        try {
            // 使用转换后的实际采样率
            val actualSampleRate = currentRequest?.actualSampleRate ?: 24000
            // 使用标准16位PCM格式
            callback?.start(actualSampleRate, AudioFormat.ENCODING_PCM_16BIT, 1)
            // 优先返回上一次缓存
            if (lastRequest != null && lastRequest?.audio != null && lastRequest?.isCompleted == true) {
                LogCollector.log(Log.DEBUG, TAG, "朗读：${lastRequest?.shortText}")
                Log.d(TAG, "朗读请求：${lastRequest?.shortText}")
                NotificationHelper.updateNotification(context.getString(R.string.tts_state_playing), lastRequest?.shortText)
                writeToCallback(callback, lastRequest!!.audio!!)
                lastRequest = null
            }

            // 如果是系统级请求要求返回当前音频
            if (isSystemRequest || !configManager.cacheAudioBookAudio) {
                // 等待异步合成流程完成
                LogCollector.log(Log.DEBUG, TAG, "朗读 ${currentRequest?.shortText}")
                Log.d(TAG, "朗读系统请求 ${currentRequest?.shortText}")
                job.join()
                NotificationHelper.updateNotification(context.getString(R.string.tts_state_playing), currentRequest?.shortText)
                writeToCallback(callback, currentRequest!!.audio!!)
                currentRequest = null
            }
            callback?.done()

        } catch (e: Exception) {
            LogCollector.log(Log.ERROR, TAG, "音频回调出错: ${e.message}")
            Log.e(TAG, "TTS回调异常", e)
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

    // 3. PCM拼接、分块写入 callback 优化
    private fun writeToCallback(callback: SynthesisCallback?, audio: ByteArray) {
        try {
            val maxBufferSize = callback?.maxBufferSize ?: return
            var offset = 0
            val total = audio.size
            val buffer = ByteArray(maxBufferSize)
            while (offset < total) {
                val bytesToWrite = maxBufferSize.coerceAtMost(total - offset)
                System.arraycopy(audio, offset, buffer, 0, bytesToWrite)
                callback.audioAvailable(buffer, 0, bytesToWrite)
                offset += bytesToWrite
            }
        } catch (e: Exception) {
            Log.e(TAG, "写入音频数据失败", e)
        }
    }

    /**
     * 将MP3转换为PCM，使用MediaCodec进行解码
     * @param mp3Data MP3音频数据
     * @return 解码后的PCM字节流（16bit小端）
     */
    private fun convertMp3ToPcm(mp3Data: ByteArray): ByteArray? {
        return convertMp3ToPcmWithMediaCodec(mp3Data)
    }

    /**
     * 用MediaCodec解码MP3为PCM
     */
    private fun convertMp3ToPcmWithMediaCodec(mp3Data: ByteArray): ByteArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        val pcmBuffer = ByteArrayOutputStream(mp3Data.size * 2) // 预估容量
        try {
            // 1. 设置内存数据源
            extractor.setDataSource(object : android.media.MediaDataSource() {
                override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                    if (position >= mp3Data.size) return -1
                    val length = minOf(size, mp3Data.size - position.toInt())
                    System.arraycopy(mp3Data, position.toInt(), buffer, offset, length)
                    return length
                }
                override fun getSize(): Long = mp3Data.size.toLong()
                override fun close() {}
            })

            // 2. 查找音频轨道
            val trackIndex = (0 until extractor.trackCount)
                .firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: return null

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null

            // 3. 配置解码器
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false

            // 4. 解码循环
            while (!sawOutputEOS) {
                // 输入数据
                if (!sawInputEOS) {
                    val inputBufferIndex = codec.dequeueInputBuffer(10000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                // 输出数据
                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputBufferIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)!!
                    if (bufferInfo.size > 0) {
                        val chunk = ByteArray(bufferInfo.size)
                        outputBuffer.get(chunk)
                        pcmBuffer.write(chunk)
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEOS = true
                    }
                }
            }
            return pcmBuffer.toByteArray()
        } catch (e: Exception) {
            LogCollector.log(Log.ERROR, TAG, "MP3解码异常: ${e.message}")
            return null
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
            try { pcmBuffer.close() } catch (_: Exception) {}
        }
    }

    fun ellipsizeMiddle(text: String, maxLength: Int = 30): String {
        if (text.length <= maxLength) return text
        return text.take(maxLength-5) + "..." + text.takeLast(5)
    }

} 