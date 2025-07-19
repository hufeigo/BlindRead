package com.hihi.ttsserver.utils

import android.app.AlertDialog
import android.content.Intent
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import com.hihi.ttsserver.R
import com.hihi.ttsserver.ui.SettingsFragment
import com.hihi.ttsserver.ui.SysTtsFragment
import com.hihi.ttsserver.ui.LogFragment
import android.net.Uri

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
                    // 检查更新，弹窗提示去 GitHub 下载
                    AlertDialog.Builder(fragment.requireContext())
                        .setTitle("检查更新")
                        .setMessage("请前往 GitHub 下载最新版应用。\n\nhttps://github.com/hufeigo/blindRead/")
                        .setPositiveButton("去GitHub") { _, _ ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hufeigo/blindRead"))
                            fragment.startActivity(intent)
                        }
                        .setNegativeButton("取消", null)
                        .show()
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.nav_about -> {
                    val context = fragment.requireContext()
                    val layout = android.widget.LinearLayout(context).apply {
                        orientation = android.widget.LinearLayout.VERTICAL
                        setPadding(48, 32, 48, 0)
                        val imageView = android.widget.ImageView(context).apply {
                            setImageResource(R.mipmap.ic_launcher)
                            val size = context.resources.displayMetrics.density * 72 // 72dp
                            layoutParams = android.widget.LinearLayout.LayoutParams(size.toInt(), size.toInt()).apply {
                                gravity = android.view.Gravity.CENTER_HORIZONTAL
                                bottomMargin = (context.resources.displayMetrics.density * 16).toInt()
                            }
                            setOnClickListener {
                                val animator = android.animation.ObjectAnimator.ofFloat(this, "rotation", 0f, 360f)
                                animator.duration = 600
                                animator.interpolator = android.view.animation.DecelerateInterpolator()
                                animator.start()
                            }
                        }
                        addView(imageView)
                        val textView = android.widget.TextView(context).apply {
                            text = """
           这个项目其实就是为了能听书方便点。原来的 ttsserver 用不了了，就用AIIDE 搞了一个新的。希望能帮到有同样需求的小伙伴～
           项目是开源的，感兴趣的话可以点下面的按钮去看看源码！
    """.trimIndent()
                            textSize = 16f
                            setPadding(
                                (context.resources.displayMetrics.density * 16).toInt(), // 段前空2格（约16dp）
                                0,
                                0,
                                (context.resources.displayMetrics.density * 8).toInt()
                            )
                            gravity = android.view.Gravity.START // 靠左
                        }
                        addView(textView)
                    }
                    AlertDialog.Builder(context)
                        .setTitle("关于")
                        .setView(layout)
                        .setPositiveButton("去GitHub") { _, _ ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hufeigo/blindRead"))
                            fragment.startActivity(intent)
                        }
                        .setNegativeButton("取消", null)
                        .show()
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                else -> false
            }
        }
    }
} 