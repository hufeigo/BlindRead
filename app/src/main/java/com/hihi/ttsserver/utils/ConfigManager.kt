package com.hihi.ttsserver.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hihi.ttsserver.App
import com.hihi.ttsserver.data.entity.TtsConfig
import com.hihi.ttsserver.data.entity.AppSettingsConfig
import com.hihi.ttsserver.model.tts.TtsVoice

class ConfigManager private constructor(context: Context) {
    private val TAG = "ConfigManager"
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "tts_configs"
        private const val KEY_CONFIGS = "configs"
        private const val KEY_APP_SETTINGS = "app_settings"
        
        @Volatile
        private var instance: ConfigManager? = null

        fun getInstance(context: Context): ConfigManager {
            return instance ?: synchronized(this) {
                instance ?: ConfigManager(context).also { instance = it }
            }
        }
    }

    /**
     * 获取所有配置
     */
    fun getAllConfigs(): List<TtsConfig> {
        try {
            val json = sharedPreferences.getString(KEY_CONFIGS, "[]")
            val type = object : TypeToken<List<TtsConfig>>() {}.type
            return gson.fromJson<List<TtsConfig>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "加载配置列表失败", e)
            return emptyList()
        }
    }

    /**
     * 获取所有启用的配置
     */
    fun getEnabledConfigs(): List<TtsConfig> {
        try {
            return getAllConfigs().filter { it.enabled }
        } catch (e: Exception) {
            Log.e(TAG, "获取启用的配置失败", e)
            return emptyList()
        }
    }

    /**
     * 获取单个启用的配置（为了向后兼容）
     */
    fun getEnabledConfig(): TtsConfig? {
        return getEnabledConfigs().firstOrNull()
    }

    /**
     * 根据ID获取配置
     */
    fun getConfigById(id: Int): TtsConfig? {
        try {
            return getAllConfigs().find { it.id == id }
        } catch (e: Exception) {
            Log.e(TAG, "获取配置失败", e)
            return null
        }
    }

    /**
     * 添加新配置
     */
    fun addConfig(config: TtsConfig) {
        try {
            val configs = getAllConfigs().toMutableList()
            val newId = if (configs.isEmpty()) 0 else configs.maxOf { it.id } + 1
            val newConfig = config.copy(id = newId)
            configs.add(newConfig)
            saveConfigs(configs)
        } catch (e: Exception) {
            Log.e(TAG, "添加配置失败", e)
            throw e
        }
    }

    /**
     * 更新配置
     */
    fun updateConfig(config: TtsConfig) {
        try {
            val configs = getAllConfigs().toMutableList()
            val index = configs.indexOfFirst { it.id == config.id }
            if (index != -1) {
                val updatedConfig = config.copy(enabled = configs[index].enabled)
                configs[index] = updatedConfig
                saveConfigs(configs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新配置失败", e)
            throw e
        }
    }

    /**
     * 删除配置
     */
    fun deleteConfig(id: Int) {
        try {
            val configs = getAllConfigs().toMutableList()
            configs.removeIf { it.id == id }
            saveConfigs(configs)
        } catch (e: Exception) {
            Log.e(TAG, "删除配置失败", e)
            throw e
        }
    }

    /**
     * 启用配置
     */
    fun enableConfig(id: Int) {
        try {
            val configs = getAllConfigs().toMutableList()
            val index = configs.indexOfFirst { it.id == id }
            if (index != -1) {
                configs[index] = configs[index].copy(enabled = true)
                saveConfigs(configs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "启用配置失败", e)
            throw e
        }
    }

    /**
     * 禁用配置
     */
    fun disableConfig(id: Int) {
        try {
            val configs = getAllConfigs().toMutableList()
            val index = configs.indexOfFirst { it.id == id }
            if (index != -1) {
                configs[index] = configs[index].copy(enabled = false)
                saveConfigs(configs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "禁用配置失败", e)
            throw e
        }
    }

    /**
     * 保存配置列表
     */
    private fun saveConfigs(configs: List<TtsConfig>) {
        try {
            val json = gson.toJson(configs)
            sharedPreferences.edit().putString(KEY_CONFIGS, json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "保存配置列表失败", e)
            throw e
        }
    }

    /**
     * 加载应用设置配置
     */
    fun loadAppSettingsConfig(): AppSettingsConfig {
        try {
            val json = sharedPreferences.getString(KEY_APP_SETTINGS, null)
            return if (json != null) {
                gson.fromJson(json, AppSettingsConfig::class.java)
            } else {
                AppSettingsConfig() // 返回默认值
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载应用设置配置失败", e)
            return AppSettingsConfig() // 出错时返回默认值
        }
    }

    /**
     * 保存应用设置配置
     */
    fun saveAppSettingsConfig(config: AppSettingsConfig) {
        try {
            val json = gson.toJson(config)
            sharedPreferences.edit().putString(KEY_APP_SETTINGS, json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "保存应用设置配置失败", e)
        }
    }

    /**
     * 公开的请求超时配置，便于外部直接访问
     */
    val requestTimeout: Int
        get() = loadAppSettingsConfig().requestTimeout

    /**
     * 公开的缓存听书音频配置，便于外部直接访问
     */
    val cacheAudioBookAudio: Boolean
        get() = loadAppSettingsConfig().cacheAudioBookAudio
}