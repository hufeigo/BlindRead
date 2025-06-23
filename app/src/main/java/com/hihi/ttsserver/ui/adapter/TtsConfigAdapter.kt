package com.hihi.ttsserver.ui.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hihi.ttsserver.data.entity.TtsConfig
import com.hihi.ttsserver.databinding.ItemTtsConfigBinding

class TtsConfigAdapter(
    private val onEditClick: (TtsConfig) -> Unit,
    private val onDeleteClick: (TtsConfig) -> Unit,
    private val onEnabledChange: (TtsConfig, Boolean) -> Unit
) : RecyclerView.Adapter<TtsConfigAdapter.ViewHolder>() {

    private val TAG = "TtsConfigAdapter"
    private var configs: List<TtsConfig> = emptyList()

    fun updateList(newConfigs: List<TtsConfig>) {
        configs = newConfigs
        notifyDataSetChanged()
    }

    fun updateItemEnabled(config: TtsConfig, enabled: Boolean) {
        val position = configs.indexOfFirst { it.id == config.id }
        if (position != -1) {
            // 更新数据
            configs = configs.toMutableList().apply {
                this[position] = config.copy(enabled = enabled)
            }
            // 只更新这一个项目
            notifyItemChanged(position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTtsConfigBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val config = configs[position]
        holder.bind(config)
    }

    override fun getItemCount(): Int = configs.size

    inner class ViewHolder(
        private val binding: ItemTtsConfigBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(config: TtsConfig) {
            try {
                binding.apply {
                    // 语音名称和角色名称
                    nameText.text = "${config.name}"

                    // 风格和强度
                    val style = if (config.voice.style.isBlank()) "无" else config.voice.style
                    val role = if (config.voice.role.isBlank()) "无" else config.voice.role
                    styleIntensityText.text = "$style-$role | 强度: ${config.styleIntensity}"

                    // 语速、音量和音高
                    rateVolumePitchText.text = "语速:${config.rate.toInt()} | 音量:${config.volume.toInt()} | 音高:${config.pitch.toInt()}"

                    // 音频格式
                    audioFormatText.text = config.audioFormat

                    // 朗读范围标签
                    scopeTag.text = config.scope

                    // API名称
                    apiNameText.text = config.apiName

                    // 设置CheckBox的初始状态和监听器
                    checkBox.setOnCheckedChangeListener(null) // 先移除旧的监听器，避免重复触发
                    checkBox.isChecked = config.enabled
                    checkBox.setOnCheckedChangeListener { _, isChecked ->
                        onEnabledChange(config, isChecked)
                    }

                    editButton.setOnClickListener {
                        onEditClick(config)
                    }

                    deleteButton.setOnClickListener {
                        onDeleteClick(config)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "绑定配置失败: ${config.voice.name}", e)
            }
        }
    }
}