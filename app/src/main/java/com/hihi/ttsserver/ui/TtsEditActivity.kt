package com.hihi.ttsserver.ui

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.hihi.ttsserver.R
import com.hihi.ttsserver.data.entity.TtsConfig
import com.hihi.ttsserver.databinding.ActivityTtsEditBinding
import com.hihi.ttsserver.model.tts.BaseTTS
import com.hihi.ttsserver.model.tts.MsTTS
import com.hihi.ttsserver.model.tts.TtsVoice
import com.hihi.ttsserver.utils.ConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class TtsEditActivity : AppCompatActivity() {
    private val TAG = "TtsEditActivity"
    private lateinit var binding: ActivityTtsEditBinding
    private lateinit var configManager: ConfigManager
    private var configId: Int? = null
    private var currentConfig: TtsConfig? = null
    private var audioTrack: AudioTrack? = null
    private val isPlaying = AtomicBoolean(false)
    private var mediaPlayer: MediaPlayer? = null
    private val gson = Gson()

    // 语音配置数据类
    data class VoiceConfig(
        val name: String,
        val displayName: String,
        val styles: List<Pair<String, String>>, // 风格列表，存储Pair<显示名, 内部名>
        val roles: List<Pair<String, String>> // 角色列表，存储Pair<显示名, 内部名>
    )

    // 语音配置列表
    private val voiceConfigs = listOf(
        VoiceConfig(
            "zh-CN-XiaoxiaoNeural",
            "晓晓 (女声, 新闻/小说, 温暖)",
            listOf("新闻播报" to "News", "小说朗读" to "Novel"),
            listOf("温暖" to "Warm")
        ),
        VoiceConfig(
            "zh-CN-XiaoyiNeural",
            "晓伊 (女声, 卡通/小说, 活泼)",
            listOf("卡通配音" to "Cartoon", "小说朗读" to "Novel"),
            listOf("活泼" to "Lively")
        ),
        VoiceConfig(
            "zh-CN-YunjianNeural",
            "云健 (男声, 体育/小说, 热情)",
            listOf("体育解说" to "Sports", "小说朗读" to "Novel"),
            listOf("热情" to "Passionate")
        ),
        VoiceConfig(
            "zh-CN-YunxiNeural",
            "云希 (男声, 小说, 活泼阳光)",
            listOf("小说朗读" to "Novel"),
            listOf("阳光" to "Sunshine")
        ),
        VoiceConfig(
            "zh-CN-YunxiaNeural",
            "云夏 (男声, 卡通/小说, 可爱)",
            listOf("卡通配音" to "Cartoon", "小说朗读" to "Novel"),
            listOf("可爱" to "Cute")
        ),
        VoiceConfig(
            "zh-CN-YunyangNeural",
            "云扬 (男声, 新闻, 专业可靠)",
            listOf("新闻播报" to "News"),
            listOf("专业" to "Professional")
        ),
        VoiceConfig(
            "zh-CN-liaoning-XiaobeiNeural",
            "晓北 (女声, 辽宁方言, 幽默)",
            listOf("方言对话" to "Dialect"),
            listOf("幽默" to "Humorous")
        ),
        VoiceConfig(
            "zh-CN-shaanxi-XiaoniNeural",
            "晓妮 (女声, 陕西方言, 明亮)",
            listOf("方言对话" to "Dialect"),
            listOf("明亮" to "Bright")
        ),
        VoiceConfig(
            "zh-HK-HiuGaaiNeural",
            "晓佳 (女声, 粤语通用, 友好积极)",
            listOf("通用对话" to "General"),
            listOf("友好" to "Friendly")
        ),
        VoiceConfig(
            "zh-HK-HiuMaanNeural",
            "晓曼 (女声, 粤语通用, 友好积极)",
            listOf("通用对话" to "General"),
            listOf("友好" to "Friendly")
        ),
        VoiceConfig(
            "zh-HK-WanLungNeural",
            "云龙 (男声, 粤语通用, 友好积极)",
            listOf("通用对话" to "General"),
            listOf("友好" to "Friendly")
        ),
        VoiceConfig(
            "zh-TW-HsiaoChenNeural",
            "晓晨 (女声, 台湾通用, 友好积极)",
            listOf("通用对话" to "General"),
            listOf("友好" to "Friendly")
        ),
        VoiceConfig(
            "zh-TW-HsiaoYuNeural",
            "晓雨 (女声, 台湾通用, 友好积极)",
            listOf("通用对话" to "General"),
            listOf("友好" to "Friendly")
        ),
        VoiceConfig(
            "zh-TW-YunJheNeural",
            "云哲 (男声, 台湾通用, 友好积极)",
            listOf("通用对话" to "General"),
            listOf("友好" to "Friendly")
        )
    )

    // 语音显示名称列表
    private val voiceDisplayNames = voiceConfigs.map { it.displayName }

    // 新增 API 和音频格式列表
    private val apis = listOf("微软Edge") // 假设只有Azure
    private val audioFormats = listOf(
        "audio-16khz-32kbitrate-mono-mp3",
        "audio-24khz-48kbitrate-mono-mp3",
        "audio-48khz-96kbitrate-mono-mp3",
        "webm-24khz-16bit-mono-opus",
        "ogg-24khz-16bit-mono-opus"
    ) // Edge支持的主流格式

    companion object {
        private const val EXTRA_CONFIG_ID = "config_id"

        fun createIntent(context: Context, configId: Int?): Intent {
            return Intent(context, TtsEditActivity::class.java).apply {
                configId?.let { putExtra(EXTRA_CONFIG_ID, it) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        binding = ActivityTtsEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configManager = ConfigManager.getInstance(this)

        configId = intent.getIntExtra(EXTRA_CONFIG_ID, -1).takeIf { it != -1 }
        Log.d(TAG, "编辑配置ID: ${configId ?: "新建"}")

        setupViews()
        setupListeners() // 确保滑块监听器在设置值之后
        loadConfig()
    }

    private fun setupViews() {
        Log.d(TAG, "设置视图")
        setSupportActionBar(binding.toolbar) // 设置 Toolbar 作为 ActionBar
        // supportActionBar?.setDisplayHomeAsUpEnabled(true) // 显示返回按钮
        binding.toolbar.setNavigationOnClickListener { // 设置返回按钮点击事件
            onBackPressedDispatcher.onBackPressed() // 返回上一个界面
        }

        binding.apply {
            // 设置播放按钮点击事件
            textInputLayout.setEndIconOnClickListener {
                Log.d(TAG, "点击播放按钮")
                doTestTTS()
            }

            // 设置下拉适配器
            voiceSpinner.setAdapter(ArrayAdapter(this@TtsEditActivity, android.R.layout.simple_dropdown_item_1line, voiceDisplayNames))
            apiSpinner.setAdapter(ArrayAdapter(this@TtsEditActivity, android.R.layout.simple_dropdown_item_1line, apis))
            audioFormatSpinner.setAdapter(ArrayAdapter(this@TtsEditActivity, android.R.layout.simple_dropdown_item_1line, audioFormats))

            // 设置默认值
            voiceSpinner.setText(voiceDisplayNames[0], false)
            apiSpinner.setText(apis[0], false)
            audioFormatSpinner.setText(audioFormats[0], false)

            // 设置为不可编辑
            voiceSpinner.setFocusable(false)
            voiceSpinner.setFocusableInTouchMode(false)
            styleSpinner.setFocusable(false)
            styleSpinner.setFocusableInTouchMode(false)
            roleSpinner.setFocusable(false)
            roleSpinner.setFocusableInTouchMode(false)
            apiSpinner.setFocusable(false)
            apiSpinner.setFocusableInTouchMode(false)
            audioFormatSpinner.setFocusable(false)
            audioFormatSpinner.setFocusableInTouchMode(false)
        }
    }

    private fun setupListeners() {
        binding.apply {
            // 语速滑块监听器
            rateSlider.addOnChangeListener { slider, value, fromUser ->
                if (fromUser) { // 仅当用户互动时更新
                    ratePercent.text = "${value.toInt()}%"
                }
            }
            // 音量滑块监听器
            volumeSlider.addOnChangeListener { slider, value, fromUser ->
                if (fromUser) {
                    volumePercent.text = "${value.toInt()}%"
                }
            }
            // 音高滑块监听器
            pitchSlider.addOnChangeListener { slider, value, fromUser ->
                if (fromUser) {
                    pitchPercent.text = "${value.toInt()}%"
                }
            }
            // 风格强度滑块监听器
            styleDegreeSlider.addOnChangeListener { slider, value, fromUser ->
                if (fromUser) {
                    styleDegreePercent.text = "${String.format("%.1f", value)}"
                }
            }

            // 语音选择监听器
            voiceSpinner.setOnItemClickListener { _, _, position, _ ->
                val selectedVoice = voiceConfigs[position]
                // 更新风格列表
                styleSpinner.setAdapter(ArrayAdapter(this@TtsEditActivity, android.R.layout.simple_dropdown_item_1line, selectedVoice.styles.map { it.first }))
                styleSpinner.setText(selectedVoice.styles[0].first, false)
                // 更新角色列表
                roleSpinner.setAdapter(ArrayAdapter(this@TtsEditActivity, android.R.layout.simple_dropdown_item_1line, selectedVoice.roles.map { it.first }))
                roleSpinner.setText(selectedVoice.roles[0].first, false)
            }

            // 按钮点击事件
            // 移除旧的测试和删除按钮监听器
            // testButton.setOnClickListener { /* 已移除 */ }
            // deleteButton.setOnClickListener { /* 已移除 */ }

            // 为新的保存按钮（Toolbar中的图标）添加监听器
            binding.toolbar.findViewById<ImageButton>(R.id.saveButton)?.setOnClickListener { // findViewById 用于Toolbar内部的View
                Log.d(TAG, "点击Toolbar保存按钮")
            saveConfig()
        }
        }
    }

    private fun loadConfig() {
        Log.d(TAG, "加载配置")
        configId?.let { id ->
            val config = configManager.getConfigById(id)
            if (config != null) {
                Log.d(TAG, "加载到配置: ${config.voice.name}")
                currentConfig = config
                binding.apply {
                    textInput.setText(if (config.text.isEmpty()) "单击右侧按钮可测试并播放" else config.text)

                    // 设置下拉选择器
                    val voiceConfig = voiceConfigs.find { it.name == config.voice.name }
                    if (voiceConfig != null) {
                        voiceSpinner.setText(voiceConfig.displayName, false)
                        // 更新风格列表
                        styleSpinner.setAdapter(ArrayAdapter(this@TtsEditActivity, android.R.layout.simple_dropdown_item_1line, voiceConfig.styles.map { it.first }))
                        styleSpinner.setText(voiceConfig.styles.find { it.second == config.voice.style }?.first ?: "", false)
                        // 更新角色列表
                        roleSpinner.setAdapter(ArrayAdapter(this@TtsEditActivity, android.R.layout.simple_dropdown_item_1line, voiceConfig.roles.map { it.first }))
                        roleSpinner.setText(voiceConfig.roles.find { it.second == config.voice.role }?.first ?: "", false)
                    }
                    apiSpinner.setText(config.apiName, false)
                    audioFormatSpinner.setText(config.audioFormat, false)

                    // 设置滑块值和百分比
                    rateSlider.value = config.rate
                    ratePercent.text = "${config.rate.toInt()}%"
                    volumeSlider.value = config.volume
                    volumePercent.text = "${config.volume.toInt()}%"
                    pitchSlider.value = config.pitch
                    pitchPercent.text = "${config.pitch.toInt()}%"
                    styleDegreeSlider.value = config.styleIntensity
                    styleDegreePercent.text = "${String.format("%.1f", config.styleIntensity)}"

                    // 设置朗读范围
                    when (config.scope) {
                        "朗读全部" -> scopeRadioGroup.check(R.id.radioAll)
                        "仅旁白" -> scopeRadioGroup.check(R.id.radioBai)
                        "仅对话" -> scopeRadioGroup.check(R.id.radioDialogue)
                    }
                }
        } else {
                Log.w(TAG, "未找到配置，ID: $id")
                Toast.makeText(this, "配置不存在", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun doTestTTS() {
        Log.d(TAG, "测试TTS")
        val text = binding.textInput.text.toString()
        if (text.isBlank()) {
            Toast.makeText(this, "请输入测试文本", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                isPlaying.set(true)
                val selectedVoiceDisplayName = binding.voiceSpinner.text.toString()
                val selectedVoice = voiceConfigs.find { it.displayName == selectedVoiceDisplayName }
                if (selectedVoice != null) {
                    val rate = binding.rateSlider.value
                    val volume = binding.volumeSlider.value
                    val pitch = binding.pitchSlider.value
                    val styleIntensity = binding.styleDegreeSlider.value
                    
                    // 获取风格的内部标识
                    val styleDisplayName = binding.styleSpinner.text.toString()
                    val styleInternalName = selectedVoice.styles.find { it.first == styleDisplayName }?.second ?: ""
                    
                    // 获取角色的内部标识
                    val roleDisplayName = binding.roleSpinner.text.toString()
                    val roleInternalName = selectedVoice.roles.find { it.first == roleDisplayName }?.second ?: ""
                    
                    val audioFormat = binding.audioFormatSpinner.text.toString()

                    val config = TtsConfig(
                        voice = TtsVoice(selectedVoice.name, styleInternalName, roleInternalName),
                        rate = rate,
                        volume = volume,
                        pitch = pitch,
                        styleIntensity = styleIntensity,
                        audioFormat = audioFormat,
                        enabled = true,
                        scope = "朗读全部"
                    )

                    val tts = MsTTS()
                    tts.updateConfig(gson.toJson(listOf(config)))
                    
                    var audioData: ByteArray? = null
                    tts.getAudioStream(text) { audio ->
                        audioData = audio
                    }
                    
                    audioData?.let { audio ->
                        withContext(Dispatchers.Main) {
                            playAudio(audio)
                        }
                    } ?: run {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@TtsEditActivity, "语音合成失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "测试TTS失败", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TtsEditActivity, "测试失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                isPlaying.set(false)
            }
        }
    }

    private fun playAudio(audioData: ByteArray) {
        try {
            // 创建临时文件
            val tempFile = File.createTempFile("temp_audio", ".mp3", cacheDir)
            tempFile.writeBytes(audioData)
            
            // 使用 MediaPlayer 播放
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.path)
                prepare()
                start()
                setOnCompletionListener {
                    release()
                    tempFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "播放音频失败", e)
        }
    }

    private fun stopPlayback() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            isPlaying.set(false)
        } catch (e: Exception) {
            Log.e(TAG, "停止播放失败", e)
        }
    }

    private fun createTts(
        voiceName: String,
        style: String,
        rate: Float,
        volume: Float,
        pitch: Float,
        styleIntensity: Float,
        role: String,
        audioFormat: String
    ): MsTTS {
        return MsTTS().apply {
            setVoice(voiceName)
            setStyle(style)
            setRate(rate)
            setVolume(volume)
            setPitch(pitch)
            setStyleIntensity(styleIntensity)
            setRole(role)
            setAudioFormat(audioFormat)
        }
    }

    private fun deleteConfig() {
        Log.d(TAG, "删除配置")
        configId?.let { id ->
            configManager.deleteConfig(id)
            Toast.makeText(this, "配置已删除", Toast.LENGTH_SHORT).show()
        finish()
        }
    }

    private fun saveConfig() {
        Log.d(TAG, "保存配置")
        val text = binding.textInput.text.toString()
        val voiceName = voiceConfigs.find { it.displayName == binding.voiceSpinner.text.toString() }?.name ?: ""

        // 获取风格的内部标识
        val selectedVoiceConfig = voiceConfigs.find { it.displayName == binding.voiceSpinner.text.toString() }
        val styleDisplayName = binding.styleSpinner.text.toString()
        val styleInternalName = selectedVoiceConfig?.styles?.find { it.first == styleDisplayName }?.second ?: ""

        // 获取角色的内部标识
        val roleDisplayName = binding.roleSpinner.text.toString()
        val roleInternalName = selectedVoiceConfig?.roles?.find { it.first == roleDisplayName }?.second ?: ""

        val rate = binding.rateSlider.value
        val volume = binding.volumeSlider.value
        val pitch = binding.pitchSlider.value
        val styleIntensity = binding.styleDegreeSlider.value
        val apiName = binding.apiSpinner.text.toString()
        val audioFormat = binding.audioFormatSpinner.text.toString()

        val scope = when (binding.scopeRadioGroup.checkedRadioButtonId) {
            R.id.radioAll -> "朗读全部"
            R.id.radioBai -> "仅旁白"
            R.id.radioDialogue -> "仅对话"
            else -> "朗读全部"
        }

        val newConfig = TtsConfig(
            id = configId ?: 0,
            name = binding.voiceSpinner.text.toString(),
            text = text,
            voice = TtsVoice(voiceName, styleInternalName, roleInternalName), // 使用内部标识
            rate = rate,
            volume = volume,
            pitch = pitch,
            styleIntensity = styleIntensity,
            apiName = apiName,
            audioFormat = audioFormat,
            scope = scope
        )

        if (configId == null) {
            configManager.addConfig(newConfig)
            Log.d(TAG, "新增配置: ${newConfig.text.take(10)}")
            Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show()
            finish() // 返回主界面
        } else {
            configManager.updateConfig(newConfig)
            Log.d(TAG, "更新配置: ${newConfig.text.take(10)}")
            Toast.makeText(this, "配置已更新", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
    }
} 