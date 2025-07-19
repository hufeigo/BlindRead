package com.hihi.ttsserver.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import com.google.android.material.textfield.TextInputEditText
import androidx.fragment.app.Fragment
import androidx.appcompat.widget.Toolbar
import androidx.core.app.NotificationCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.GravityCompat
import com.google.android.material.navigation.NavigationView
import com.hihi.ttsserver.R
import com.hihi.ttsserver.data.entity.AppSettingsConfig
import com.hihi.ttsserver.utils.ConfigManager
import com.hihi.ttsserver.utils.NavigationHandler

class SettingsFragment : Fragment() {
    companion object {
        private const val TAG = "SettingsFragment"
    }

    private lateinit var configManager: ConfigManager
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toolbar: Toolbar
    private lateinit var navigationView: NavigationView

    // 应用设置UI元素
    private lateinit var cbSplitLongSentences: CheckBox
    private lateinit var cbMultiLanguageEnabled: CheckBox
    private lateinit var cbPlayAudioInApp: CheckBox
    private lateinit var cbCacheAudioBookAudio: CheckBox
    private lateinit var cbMultipleSelectionForSameReadingTarget: CheckBox
    private lateinit var etMinDialogueChineseCharacters: TextInputEditText
    private lateinit var etRequestTimeout: TextInputEditText

    // 应用设置配置
    private var appSettingsConfig: AppSettingsConfig = AppSettingsConfig()

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            configManager = ConfigManager.getInstance(requireContext())
        } catch (e: Exception) {
            Log.e(TAG, "初始化失败", e)
            throw e
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        try {
            return inflater.inflate(R.layout.fragment_settings, container, false)
        } catch (e: Exception) {
            Log.e(TAG, "创建视图失败", e)
            throw e
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 初始化设置界面
        try {
            toolbar = view.findViewById(R.id.toolbar)
            drawerLayout = view.findViewById(R.id.drawer_layout)
            navigationView = view.findViewById(R.id.nav_view)

            // 设置Toolbar的导航图标点击事件来打开抽屉
            toolbar.setNavigationOnClickListener { 
                drawerLayout.openDrawer(GravityCompat.START)
            }
            
            // 可选：集成ActionBarDrawerToggle来同步抽屉状态和图标动画
            /* 
            val toggle = ActionBarDrawerToggle(
                activity, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
            )
            drawerLayout.addDrawerListener(toggle)
            toggle.syncState()
            */

            // 设置导航菜单
            NavigationHandler.setupNavigation(this, drawerLayout, navigationView)

            cbSplitLongSentences = view.findViewById(R.id.cbSplitLongSentences)
            cbMultiLanguageEnabled = view.findViewById(R.id.cbMultiLanguageEnabled)
            cbPlayAudioInApp = view.findViewById(R.id.cbPlayAudioInApp)
            cbCacheAudioBookAudio = view.findViewById(R.id.cbCacheAudioBookAudio)
	        cbMultipleSelectionForSameReadingTarget = view.findViewById(R.id.cbMultipleSelectionForSameReadingTarget)
            etMinDialogueChineseCharacters = view.findViewById(R.id.etMinDialogueChineseCharacters)
            etRequestTimeout = view.findViewById(R.id.etRequestTimeout)

            loadAppSettings()
            setupAppSettingsListeners()
        } catch (e: Exception) {
            Log.e(TAG, "初始化视图失败", e)
            throw e
        }
    }

    private fun loadAppSettings() {
        appSettingsConfig = configManager.loadAppSettingsConfig()
        cbSplitLongSentences.isChecked = appSettingsConfig.splitLongSentences
        cbMultiLanguageEnabled.isChecked = appSettingsConfig.multiLanguageEnabled
        cbPlayAudioInApp.isChecked = appSettingsConfig.playAudioInApp
        cbCacheAudioBookAudio.isChecked = appSettingsConfig.cacheAudioBookAudio
	cbMultipleSelectionForSameReadingTarget.isChecked = appSettingsConfig.multipleSelectionForSameReadingTarget
        etMinDialogueChineseCharacters.setText(appSettingsConfig.minDialogueChineseCharacters.toString())
        etRequestTimeout.setText(appSettingsConfig.requestTimeout.toString())
    }

    private fun setupAppSettingsListeners() {
        cbSplitLongSentences.setOnCheckedChangeListener { _, isChecked ->
            appSettingsConfig = appSettingsConfig.copy(splitLongSentences = isChecked)
            configManager.saveAppSettingsConfig(appSettingsConfig)
        }
        cbMultiLanguageEnabled.setOnCheckedChangeListener { _, isChecked ->
            appSettingsConfig = appSettingsConfig.copy(multiLanguageEnabled = isChecked)
            configManager.saveAppSettingsConfig(appSettingsConfig)
        }
        cbPlayAudioInApp.setOnCheckedChangeListener { _, isChecked ->
            appSettingsConfig = appSettingsConfig.copy(playAudioInApp = isChecked)
            configManager.saveAppSettingsConfig(appSettingsConfig)
        }
        cbCacheAudioBookAudio.setOnCheckedChangeListener { _, isChecked ->
            appSettingsConfig = appSettingsConfig.copy(cacheAudioBookAudio = isChecked)
            configManager.saveAppSettingsConfig(appSettingsConfig)
        }
	    cbMultipleSelectionForSameReadingTarget.setOnCheckedChangeListener { _, isChecked ->
            appSettingsConfig = appSettingsConfig.copy(multipleSelectionForSameReadingTarget = isChecked)
            configManager.saveAppSettingsConfig(appSettingsConfig)
        }

        etMinDialogueChineseCharacters.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val value = s.toString().toIntOrNull() ?: 0
                appSettingsConfig = appSettingsConfig.copy(minDialogueChineseCharacters = value)
                configManager.saveAppSettingsConfig(appSettingsConfig)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etRequestTimeout.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val value = s.toString().toIntOrNull() ?: 5000
                appSettingsConfig = appSettingsConfig.copy(requestTimeout = value)
                configManager.saveAppSettingsConfig(appSettingsConfig)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        loadAppSettings()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        configManager.saveAppSettingsConfig(appSettingsConfig)
    }
} 