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
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusBadge: TextView
    private lateinit var logTextView: TextView
    private lateinit var enableSwitch: Switch
    private lateinit var vibrateSwitch: Switch
    private lateinit var modeSpinner: Spinner

    private lateinit var btnMapUp: Button
    private lateinit var btnMapDown: Button
    private lateinit var btnMapSelect: Button
    private lateinit var btnMapBack: Button

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val text = intent?.getStringExtra(RingBridgeService.EXTRA_LOG_TEXT) ?: return
            logTextView.text = text
            updateMappingButtons()
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
        updateMappingButtons()
        logTextView.text = BridgeConfig.getLastLog(this)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(logReceiver)
        RingBridgeService.learningTarget = null
    }

    private fun setupUI() {
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        // 타이틀
        val title = TextView(this).apply {
            text = "R08 ↔ MemoMind One 브릿지"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 24)
        }
        container.addView(title)

        // 서비스 활성화 상태 배지
        statusBadge = TextView(this).apply {
            textSize = 15f
            setPadding(32, 24, 32, 24)
            gravity = Gravity.CENTER
        }
        container.addView(statusBadge)

        // 접근성 설정 이동 버튼
        val btnOpenAccessibility = Button(this).apply {
            text = "1단계: 안드로이드 접근성 서비스 켜기"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        container.addView(btnOpenAccessibility, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 24 })

        // 브릿지 마스터 스위치
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

        // 진동 피드백 스위치
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

        // 탐색 모드 선택
        val modeLabel = TextView(this).apply {
            text = "MemoMind 메뉴 제어 방식"
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 16, 0, 8)
        }
        container.addView(modeLabel)

        val modes = arrayOf(
            "하이브리드 (포커스 이동 우선 + 스와이프)",
            "스와이프 전용 (대시보드/프롬프터 스크롤)",
            "UI 포커스 이동 전용 (메뉴 버튼 탐색)"
        )
        modeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, modes)
            setSelection(BridgeConfig.getNavMode(this@MainActivity))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                    BridgeConfig.setNavMode(this@MainActivity, pos)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        container.addView(modeSpinner)

        // 키 매핑 섹션 (직접 링 제스처 학습 가능)
        val mapHeader = TextView(this).apply {
            text = "2단계: R08 링 제스처 키 매핑 (클릭 후 링 조작)"
            setTypeface(null, Typeface.BOLD)
            textSize = 16f
            setPadding(0, 40, 0, 16)
        }
        container.addView(mapHeader)

        btnMapUp = Button(this).apply {
            setOnClickListener { startKeyLearning("UP", this) }
        }
        container.addView(btnMapUp)

        btnMapDown = Button(this).apply {
            setOnClickListener { startKeyLearning("DOWN", this) }
        }
        container.addView(btnMapDown)

        btnMapSelect = Button(this).apply {
            setOnClickListener { startKeyLearning("SELECT", this) }
        }
        container.addView(btnMapSelect)

        btnMapBack = Button(this).apply {
            setOnClickListener { startKeyLearning("BACK", this) }
        }
        container.addView(btnMapBack)

        // 실시간 키 이벤트 모니터
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

    private fun startKeyLearning(target: String, button: Button) {
        if (!RingBridgeService.isServiceRunning) {
            Toast.makeText(this, "먼저 1단계 접근성 서비스를 활성화해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        RingBridgeService.learningTarget = target
        button.text = "지금 R08 링 제스처를 입력하세요..."
        logTextView.text = "[$target] 키 학습 대기 중... 링을 조작해주세요."
    }

    private fun updateMappingButtons() {
        btnMapUp.text = "이전 메뉴 (Up): ${KeyEvent.keyCodeToString(BridgeConfig.getUpKeyCode(this))}"
        btnMapDown.text = "다음 메뉴 (Down): ${KeyEvent.keyCodeToString(BridgeConfig.getDownKeyCode(this))}"
        btnMapSelect.text = "선택/실행 (Tap): ${KeyEvent.keyCodeToString(BridgeConfig.getSelectKeyCode(this))}"
        btnMapBack.text = "뒤로가기 (Back): ${KeyEvent.keyCodeToString(BridgeConfig.getBackKeyCode(this))}"
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
