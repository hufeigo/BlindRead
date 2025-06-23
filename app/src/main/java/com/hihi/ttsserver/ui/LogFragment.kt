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
import com.hihi.ttsserver.utils.MemoryMonitor
import com.google.android.material.navigation.NavigationView
import android.util.Log
import android.widget.ScrollView
import android.widget.ImageButton
import android.os.Handler
import android.os.Looper
import java.util.LinkedList

class LogFragment : Fragment() {
    private lateinit var logTextView: TextView
    private lateinit var memoryInfoView: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var toolbar: Toolbar
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var btnToggleLog: ImageButton
    private var isCollecting = false
    private val handler = Handler(Looper.getMainLooper())
    private var isMonitoringMemory = false
    private var initialMemoryInfo: MemoryMonitor.MemoryInfo? = null
    private val maxLines = 500
    private val logQueue: LinkedList<String> = LinkedList()

    private val memoryMonitorRunnable = object : Runnable {
        override fun run() {
            if (isMonitoringMemory) {
                val currentInfo = MemoryMonitor.getMemoryInfo(requireContext())
                updateMemoryInfoDisplay(currentInfo)
                handler.postDelayed(this, 5000) // 每5秒记录一次内存状态
            }
        }
    }

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
        memoryInfoView = view.findViewById(R.id.memoryInfoView)

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
        initialMemoryInfo = MemoryMonitor.getMemoryInfo(requireContext())
        updateMemoryInfoDisplay(initialMemoryInfo!!)

        // 开始收集日志
        startLogCollection()
        
        // 添加一条测试日志
        LogCollector.log(Log.INFO, "LogFragment", "日志界面已启动")
    }

    private fun updateMemoryInfoDisplay(currentInfo: MemoryMonitor.MemoryInfo) {
        val initialInfo = initialMemoryInfo ?: currentInfo
        
        // 获取单位
        val unit = getUnit(currentInfo.dalvikHeapAllocated)
        
        // 转换所有值为指定单位
        val currentD = convertToUnit(currentInfo.dalvikHeapAllocated, unit)
        val initialD = convertToUnit(initialInfo.dalvikHeapAllocated, unit)
        val currentR = convertToUnit(currentInfo.vmRss * 1024, unit)
        val initialR = convertToUnit(initialInfo.vmRss * 1024, unit)
        val currentN = convertToUnit(currentInfo.nativeHeapAllocated, unit)
        val initialN = convertToUnit(initialInfo.nativeHeapAllocated, unit)

        val memoryInfo = "$unit D:$currentD($initialD) R:$currentR($initialR) N:$currentN($initialN)"

        activity?.runOnUiThread {
            memoryInfoView.text = memoryInfo
        }
    }

    private fun getUnit(size: Long): String {
        return when {
            size >= 1024 * 1024 * 1024 -> "GB"
            size >= 1024 * 1024 -> "MB"
            size >= 1024 -> "KB"
            else -> "B"
        }
    }

    private fun convertToUnit(size: Long, unit: String): String {
        val value = when (unit) {
            "GB" -> size / (1024.0 * 1024 * 1024)
            "MB" -> size / (1024.0 * 1024)
            "KB" -> size / 1024.0
            else -> size.toDouble()
        }
        return String.format("%.1f", value)
    }

    private fun startLogCollection() {
        isCollecting = true
        btnToggleLog.setImageResource(R.drawable.ic_pause_24px)
        LogCollector.startCollecting { log ->
            if (isCollecting) {
                addLog(log)
            }
        }
        LogCollector.log(Log.INFO, "LogFragment", "开始收集日志")
        
        // 开始内存监控
        startMemoryMonitoring()
    }

    private fun stopLogCollection() {
        isCollecting = false
        btnToggleLog.setImageResource(R.drawable.ic_play_24px)
        LogCollector.stopCollecting()
        LogCollector.log(Log.INFO, "LogFragment", "停止收集日志")
        
        // 停止内存监控
        stopMemoryMonitoring()
    }

    private fun startMemoryMonitoring() {
        isMonitoringMemory = true
        handler.post(memoryMonitorRunnable)
    }

    private fun stopMemoryMonitoring() {
        isMonitoringMemory = false
        handler.removeCallbacks(memoryMonitorRunnable)
    }

    private fun addLog(log: String) {
        logQueue.add(log)
        if (logQueue.size > maxLines) {
            logQueue.removeFirst()
        }
        requireActivity().runOnUiThread {
            logTextView.text = logQueue.joinToString("\n")
            scrollView.post {
                scrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isCollecting = false
        stopMemoryMonitoring()
        LogCollector.stopCollecting()
    }
} 