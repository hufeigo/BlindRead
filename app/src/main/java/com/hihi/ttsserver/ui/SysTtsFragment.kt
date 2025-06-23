package com.hihi.ttsserver.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.CheckBox
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.view.GravityCompat
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textfield.TextInputEditText
import com.hihi.ttsserver.R
import com.hihi.ttsserver.ui.adapter.TtsConfigAdapter
import com.hihi.ttsserver.data.entity.TtsConfig
import com.hihi.ttsserver.data.entity.AppSettingsConfig
import com.hihi.ttsserver.databinding.FragmentSysttsBinding
import com.hihi.ttsserver.utils.ConfigManager
import com.hihi.ttsserver.utils.NavigationHandler
import android.widget.Toast

class SysTtsFragment : Fragment() {
    private val TAG = "SysTtsFragment"
    private var _binding: FragmentSysttsBinding? = null
    private val binding get() = _binding!!
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TtsConfigAdapter
    private lateinit var toolbar: Toolbar
    private lateinit var btnAddNewConfig: ImageButton
    private lateinit var tvEmptyState: TextView
    private lateinit var ivEmptyStateIcon: ImageView
    private lateinit var configManager: ConfigManager
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configManager = ConfigManager.getInstance(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSysttsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupRecyclerView()
        setupAddNewConfigButton()
        loadConfigs()
    }

    private fun initViews(view: View) {
        try {
            toolbar = view.findViewById(R.id.toolbar)
            drawerLayout = view.findViewById(R.id.drawer_layout)
            navigationView = view.findViewById(R.id.nav_view)
            recyclerView = view.findViewById(R.id.recyclerView)
            btnAddNewConfig = view.findViewById(R.id.btnAddNewConfig)
            tvEmptyState = view.findViewById(R.id.tvEmptyState)
            ivEmptyStateIcon = view.findViewById(R.id.ivEmptyStateIcon)

            // 设置Toolbar的导航图标点击事件来打开抽屉
            toolbar.setNavigationOnClickListener { 
                drawerLayout.openDrawer(GravityCompat.START)
            }
            // 设置导航菜单
            NavigationHandler.setupNavigation(this, drawerLayout, navigationView)
            /* 
            // 集成ActionBarDrawerToggle来同步抽屉状态和图标动画
            val toggle = ActionBarDrawerToggle(
                activity, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
            )
            drawerLayout.addDrawerListener(toggle)
            toggle.syncState()
            */
            recyclerView.layoutManager = LinearLayoutManager(context)
        } catch (e: Exception) {
            Log.e(TAG, "初始化视图失败", e)
            throw e
        }
    }

    private fun setupRecyclerView() {
        adapter = TtsConfigAdapter(
            onEditClick = { config -> openEditActivity(config) },
            onDeleteClick = { config -> deleteConfig(config) },
            onEnabledChange = { config, enabled -> updateConfigEnabled(config, enabled) }
        )
        recyclerView.adapter = adapter
    }

    private fun setupAddNewConfigButton() {
        btnAddNewConfig.setOnClickListener {
            openEditActivity(null)
        }
    }

    private fun loadConfigs() {
        val configs = configManager.getAllConfigs()
        adapter.updateList(configs)
        if (configs.isEmpty()) {
            tvEmptyState.visibility = View.VISIBLE
            ivEmptyStateIcon.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            tvEmptyState.visibility = View.GONE
            ivEmptyStateIcon.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun openEditActivity(config: TtsConfig?) {
        val intent = TtsEditActivity.createIntent(requireContext(), config?.id)
        startActivity(intent)
    }

    private fun deleteConfig(config: TtsConfig) {
        configManager.deleteConfig(config.id)
        loadConfigs()
    }

    private fun updateConfigEnabled(config: TtsConfig, enabled: Boolean) {
        // 获取应用设置
        val appSettings = configManager.loadAppSettingsConfig()
        
        if (enabled) {
            // 检查是否允许启用该配置
            when (config.scope) {
                "仅旁白", "仅对话" -> {
                    if (!appSettings.multiLanguageEnabled) {
                        // 如果多语言未启用，提示用户
                        Toast.makeText(requireContext(), "请先在设置中开启\"多语言(旁白/对白)\"功能", Toast.LENGTH_SHORT).show()
                        // 恢复选中状态
                        adapter.updateItemEnabled(config, false)
                        return
                    }
                    
                    // 只有在多选关闭时才检查是否已有相同类型的配置
                    if (!appSettings.multipleSelectionForSameReadingTarget) {
                        val enabledConfigs = configManager.getAllConfigs().filter { it.enabled }
                        if (enabledConfigs.any { it.scope == config.scope }) {
                            Toast.makeText(requireContext(), "已有一个${config.scope}配置启用，请先关闭或开启\"相同朗读目标可多选\"功能", Toast.LENGTH_SHORT).show()
                            // 恢复选中状态
                            adapter.updateItemEnabled(config, false)
                            return
                        }
                    }
                }
                "朗读全部" -> {
                    // 只有在多选关闭时才检查是否已有朗读全部配置
                    if (!appSettings.multipleSelectionForSameReadingTarget) {
                        val enabledConfigs = configManager.getAllConfigs().filter { it.enabled }
                        if (enabledConfigs.any { it.scope == "朗读全部" }) {
                            Toast.makeText(requireContext(), "已有一个朗读全部配置启用，请先关闭或开启\"相同朗读目标可多选\"功能", Toast.LENGTH_SHORT).show()
                            // 恢复选中状态
                            adapter.updateItemEnabled(config, false)
                            return
                        }
                    }
                }
            }
            
            // 启用配置
            configManager.enableConfig(config.id)
        } else {
            // 禁用配置
            configManager.disableConfig(config.id)
        }
        
        // 重新加载配置列表
        loadConfigs()
    }

    override fun onResume() {
        super.onResume()
        loadConfigs()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 