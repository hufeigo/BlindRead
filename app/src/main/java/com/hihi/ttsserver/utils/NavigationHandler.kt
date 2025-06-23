package com.hihi.ttsserver.utils

import android.content.Intent
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import com.hihi.ttsserver.R
import com.hihi.ttsserver.ui.SettingsFragment
import com.hihi.ttsserver.ui.SysTtsFragment
import com.hihi.ttsserver.ui.LogFragment

object NavigationHandler {
    fun setupNavigation(
        fragment: Fragment,
        drawerLayout: DrawerLayout,
        navigationView: NavigationView
    ) {
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_tts_config -> {
                    // 跳转到系统TTS配置界面
                    fragment.parentFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainerView, SysTtsFragment())
                        .commit()
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.nav_app_settings -> {
                    // 跳转到应用配置界面
                    fragment.parentFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainerView, SettingsFragment())
                        .commit()
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.nav_log -> {
                    // 跳转到运行日志界面
                    fragment.parentFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainerView, LogFragment())
                        .commit()
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.nav_battery -> {
                    // 打开电池优化白名单设置
                    val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    fragment.startActivity(intent)
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.nav_update -> {
                    // 检查更新
                    // TODO: 实现检查更新功能
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.nav_about -> {
                    // 关于
                    // TODO: 实现关于界面
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                else -> false
            }
        }
    }
} 