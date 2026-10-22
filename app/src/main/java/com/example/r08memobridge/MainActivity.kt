package com.example.r08memobridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusBadge: TextView
    private lateinit var logTextView: TextView
    private lateinit var enableSwitch: Switch
    private lateinit var vibrateSwitch: Switch

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val text = intent?.getStringExtra(RingBridgeService.EXTRA_LOG_TEXT) ?: return
            logTextView.text = text
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUI()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(RingBridgeService.ACTION_KEY_LOG)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(logReceiver, filter)
        }
        updateServiceStatus()
        logTextView.text = BridgeConfig.getLastLog(this)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(logReceiver)
    }

    private fun setupUI() {
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = "R08 ↔ MemoMind One 브릿지"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 24)
        }
        container.addView(title)

        statusBadge = TextView(this).apply {
            textSize = 15f
            setPadding(32, 24, 32, 24)
            gravity = Gravity.CENTER
        }
        container.addView(statusBadge)

        val btnOpenAccessibility = Button(this).apply {
            text = "1단계: 안드로이드 접근성 서비스 켜기"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        container.addView(btnOpenAccessibility, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 24 })

        enableSwitch = Switch(this).apply {
            text = "R08 링 신호 변환 활성화"
            textSize = 16f
            isChecked = BridgeConfig.isBridgeEnabled(this@MainActivity)
            setPadding(0, 32, 0, 16)
            setOnCheckedChangeListener { _, isChecked ->
                BridgeConfig.setBridgeEnabled(this@MainActivity, isChecked)
            }
        }
        container.addView(enableSwitch)

        vibrateSwitch = Switch(this).apply {
            text = "신호 수신 시 짧은 진동 피드백"
            textSize = 16f
            isChecked = BridgeConfig.isVibrateEnabled(this@MainActivity)
            setPadding(0, 16, 0, 24)
            setOnCheckedChangeListener { _, isChecked ->
                BridgeConfig.setVibrateEnabled(this@MainActivity, isChecked)
            }
        }
        container.addView(vibrateSwitch)

        val logLabel = TextView(this).apply {
            text = "실시간 R08 신호 수신 모니터"
            setTypeface(null, Typeface.BOLD)
            textSize = 16f
            setPadding(0, 40, 0, 12)
        }
        container.addView(logLabel)

        logTextView = TextView(this).apply {
            text = BridgeConfig.getLastLog(this@MainActivity)
            textSize = 14f
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.parseColor("#EEEEEE"))
            setTextColor(Color.parseColor("#222222"))
        }
        container.addView(logTextView)

        scroll.addView(container)
        setContentView(scroll)
    }

    private fun updateServiceStatus() {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        val isEnabled = enabledServices.contains(packageName)

        if (isEnabled) {
            statusBadge.text = "✅ 접근성 브릿지 서비스 작동 중"
            statusBadge.setBackgroundColor(Color.parseColor("#E8F5E9"))
            statusBadge.setTextColor(Color.parseColor("#2E7D32"))
        } else {
            statusBadge.text = "⚠️ 접근성 서비스가 꺼져 있습니다 (1단계 버튼을 눌러 켜주세요)"
            statusBadge.setBackgroundColor(Color.parseColor("#FFEBEE"))
            statusBadge.setTextColor(Color.parseColor("#C62828"))
        }
    }
}
