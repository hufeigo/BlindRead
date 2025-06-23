package com.hihi.ttsserver.utils

import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList
import java.text.SimpleDateFormat
import java.util.*

object LogCollector {
    private const val TAG = "LogCollector"
    private val logListeners = CopyOnWriteArrayList<(String) -> Unit>()
    private var isCollecting = false
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun startCollecting(listener: (String) -> Unit) {
        if (!isCollecting) {
            isCollecting = true
            logListeners.add(listener)
        }
    }

    fun stopCollecting() {
        isCollecting = false
        logListeners.clear()
    }

    fun isCollecting(): Boolean = isCollecting

    fun log(priority: Int, tag: String, message: String) {
        // 先调用系统日志
        Log.println(priority, tag, message)
        
        // 如果正在收集，则通知监听器
        if (isCollecting) {
            val timestamp = dateFormat.format(Date())
            val logMessage = "[$timestamp] ${getPriorityString(priority)}/$tag: $message"
            logListeners.forEach { it(logMessage) }
        }
    }

    private fun getPriorityString(priority: Int): String {
        return when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "A"
            else -> "?"
        }
    }
} 