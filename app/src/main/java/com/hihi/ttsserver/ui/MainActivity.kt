package com.hihi.ttsserver.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.hihi.ttsserver.R
import com.hihi.ttsserver.databinding.ActivityMainBinding
import com.hihi.ttsserver.utils.PermissionUtils

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var bottomNav: BottomNavigationView

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            // 直接显示 SysTtsFragment
            if (savedInstanceState == null) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainerView, SysTtsFragment())
                    .commit()
            }

            checkOverlayPermission()
        } catch (e: Exception) {
            Log.e(TAG, "初始化失败", e)
            throw e
        }
    }

    private fun checkOverlayPermission() {
        if (!PermissionUtils.checkOverlayPermission(this)) {
            Toast.makeText(
                this,
                "需要悬浮窗权限才能正常运行，请授予权限",
                Toast.LENGTH_LONG
            ).show()
            PermissionUtils.requestOverlayPermission(this)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PermissionUtils.OVERLAY_PERMISSION_REQ_CODE) {
            if (PermissionUtils.checkOverlayPermission(this)) {
                Toast.makeText(this, "悬浮窗权限已授予", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "悬浮窗权限被拒绝，部分功能可能无法使用", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onStart() {
        try {
            super.onStart()
        } catch (e: Exception) {
            Log.e(TAG, "onStart失败", e)
            throw e
        }
    }

    override fun onResume() {
        try {
            super.onResume()
        } catch (e: Exception) {
            Log.e(TAG, "onResume失败", e)
            throw e
        }
    }
} 