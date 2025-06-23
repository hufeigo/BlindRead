package com.hihi.ttsserver.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import android.util.Log
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException

object MemoryMonitor {
    private const val TAG = "MemoryMonitor"

    data class MemoryInfo(
        val totalMemory: Long,
        val availableMemory: Long,
        val usedMemory: Long,
        val nativeHeapSize: Long,
        val nativeHeapAllocated: Long,
        val nativeHeapFree: Long,
        val dalvikHeapSize: Long,
        val dalvikHeapAllocated: Long,
        val dalvikHeapFree: Long,
        val vmRss: Long = 0,
        val vmSize: Long = 0,
        val vmSwap: Long = 0
    )

    fun getMemoryInfo(context: Context): MemoryInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val runtime = Runtime.getRuntime()
        val nativeHeapSize = Debug.getNativeHeapSize()
        val nativeHeapAllocated = Debug.getNativeHeapAllocatedSize()
        val nativeHeapFree = Debug.getNativeHeapFreeSize()

        val dalvikHeapSize = runtime.totalMemory()
        val dalvikHeapAllocated = dalvikHeapSize - runtime.freeMemory()
        val dalvikHeapFree = runtime.freeMemory()

        // 获取进程内存信息
        val processInfo = getProcessMemoryInfo()
        
        return MemoryInfo(
            totalMemory = memoryInfo.totalMem,
            availableMemory = memoryInfo.availMem,
            usedMemory = memoryInfo.totalMem - memoryInfo.availMem,
            nativeHeapSize = nativeHeapSize,
            nativeHeapAllocated = nativeHeapAllocated,
            nativeHeapFree = nativeHeapFree,
            dalvikHeapSize = dalvikHeapSize,
            dalvikHeapAllocated = dalvikHeapAllocated,
            dalvikHeapFree = dalvikHeapFree,
            vmRss = processInfo.vmRss,
            vmSize = processInfo.vmSize,
            vmSwap = processInfo.vmSwap
        )
    }

    data class ProcessMemoryInfo(
        val vmRss: Long = 0,
        val vmSize: Long = 0,
        val vmSwap: Long = 0
    )

    private fun getProcessMemoryInfo(): ProcessMemoryInfo {
        val pid = Process.myPid()
        var vmRss = 0L
        var vmSize = 0L
        var vmSwap = 0L
        
        try {
            val reader = BufferedReader(FileReader("/proc/$pid/status"))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                when {
                    line?.startsWith("VmRSS:") == true -> {
                        vmRss = line?.split("\\s+".toRegex())?.get(1)?.toLongOrNull() ?: 0
                    }
                    line?.startsWith("VmSize:") == true -> {
                        vmSize = line?.split("\\s+".toRegex())?.get(1)?.toLongOrNull() ?: 0
                    }
                    line?.startsWith("VmSwap:") == true -> {
                        vmSwap = line?.split("\\s+".toRegex())?.get(1)?.toLongOrNull() ?: 0
                    }
                }
            }
            reader.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error reading process memory info", e)
        }
        
        return ProcessMemoryInfo(vmRss, vmSize, vmSwap)
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
} 