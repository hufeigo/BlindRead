package com.hihi.ttsserver.ui

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.GravityCompat
import com.hihi.ttsserver.R
import com.hihi.ttsserver.utils.LogCollector
import com.hihi.ttsserver.utils.NavigationHandler
import com.google.android.material.navigation.NavigationView
import android.util.Log
import android.widget.ScrollView
import android.widget.ImageButton
import android.os.Handler
import android.os.Looper
import java.util.LinkedList

class LogFragment : Fragment() {
    private lateinit var logTextView: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var toolbar: Toolbar
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var btnToggleLog: ImageButton
    private var isCollecting = false
    private val handler = Handler(Looper.getMainLooper())
    private val maxLines = 500
    private val logQueue: LinkedList<String> = LinkedList()
    private val logLock = Any()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_log, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 设置工具栏
        toolbar = view.findViewById(R.id.toolbar)
        drawerLayout = view.findViewById(R.id.drawer_layout)
        navigationView = view.findViewById(R.id.nav_view)
        btnToggleLog = view.findViewById(R.id.btnStart)

        // 设置Toolbar的导航图标点击事件来打开抽屉
        toolbar.setNavigationOnClickListener { 
            drawerLayout.openDrawer(GravityCompat.START)
        }
        // 设置导航菜单
        NavigationHandler.setupNavigation(this, drawerLayout, navigationView)
        
        // 设置日志文本视图和滚动视图
        logTextView = view.findViewById(R.id.logTextView)
        scrollView = view.findViewById(R.id.scrollView)
        logTextView.movementMethod = ScrollingMovementMethod()
        
        // 设置滚动视图的初始状态
        scrollView.isSmoothScrollingEnabled = true

        // 设置日志收集按钮点击事件
        btnToggleLog.setOnClickListener {
            if (isCollecting) {
                stopLogCollection()
            } else {
                startLogCollection()
            }
        }

        // 记录初始内存状态
        // 移除 memoryInfoView、isMonitoringMemory、initialMemoryInfo、memoryMonitorRunnable 相关声明和所有相关逻辑
        // 移除 updateMemoryInfoDisplay、getUnit、convertToUnit、startMemoryMonitoring、stopMemoryMonitoring 方法
        // 移除 onViewCreated、startLogCollection、stopLogCollection、onDestroyView 中所有与内存监控相关的调用和UI操作

        // 开始收集日志
        startLogCollection()
        
        // 添加一条测试日志
        LogCollector.log(Log.INFO, "LogFragment", "日志界面已启动")
    }

    // 移除 updateMemoryInfoDisplay、getUnit、convertToUnit、startMemoryMonitoring、stopMemoryMonitoring 方法

    private fun startLogCollection() {
        isCollecting = true
        btnToggleLog.setImageResource(R.drawable.ic_pause_24px)
        LogCollector.startCollecting { log ->
            if (isCollecting) {
                addLog(log)
            }
        }
        LogCollector.log(Log.INFO, "LogFragment", "开始收集日志")
        
        // 移除 memoryInfoView、isMonitoringMemory、initialMemoryInfo、memoryMonitorRunnable 相关声明和所有相关逻辑
        // 移除 updateMemoryInfoDisplay、getUnit、convertToUnit、startMemoryMonitoring、stopMemoryMonitoring 方法
        // 移除 onViewCreated、startLogCollection、stopLogCollection、onDestroyView 中所有与内存监控相关的调用和UI操作
    }

    private fun stopLogCollection() {
        isCollecting = false
        btnToggleLog.setImageResource(R.drawable.ic_play_24px)
        LogCollector.stopCollecting()
        LogCollector.log(Log.INFO, "LogFragment", "停止收集日志")
        
        // 移除 memoryInfoView、isMonitoringMemory、initialMemoryInfo、memoryMonitorRunnable 相关声明和所有相关逻辑
        // 移除 updateMemoryInfoDisplay、getUnit、convertToUnit、startMemoryMonitoring、stopMemoryMonitoring 方法
        // 移除 onViewCreated、startLogCollection、stopLogCollection、onDestroyView 中所有与内存监控相关的调用和UI操作
    }

    private fun addLog(log: String) {
        synchronized(logLock) {
        logQueue.add(log)
        if (logQueue.size > maxLines) {
            logQueue.removeFirst()
        }
        }
        refreshLogView()
    }

    private fun clearLogs() {
        synchronized(logLock) {
            logQueue.clear()
        }
        refreshLogView()
    }

    private fun getLogs(): List<String> {
        synchronized(logLock) {
            return logQueue.toList()
        }
    }

    private fun refreshLogView() {
        requireActivity().runOnUiThread {
            val logs = getLogs()
            logTextView.text = logs.joinToString("\n")
            scrollView.post {
                scrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isCollecting = false
        // 移除 memoryInfoView、isMonitoringMemory、initialMemoryInfo、memoryMonitorRunnable 相关声明和所有相关逻辑
        // 移除 updateMemoryInfoDisplay、getUnit、convertToUnit、startMemoryMonitoring、stopMemoryMonitoring 方法
        // 移除 onViewCreated、startLogCollection、stopLogCollection、onDestroyView 中所有与内存监控相关的调用和UI操作
        LogCollector.stopCollecting()
    }
} 