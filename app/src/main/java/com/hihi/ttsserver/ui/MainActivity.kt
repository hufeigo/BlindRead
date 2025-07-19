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
        } catch (e: Exception) {
            Log.e(TAG, "初始化失败", e)
            throw e
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