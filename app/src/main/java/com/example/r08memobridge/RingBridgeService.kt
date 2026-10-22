package com.example.r08memobridge

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class RingBridgeService : AccessibilityService() {

    companion object {
        const val ACTION_KEY_LOG = "com.example.r08memobridge.ACTION_KEY_LOG"
        const val EXTRA_LOG_TEXT = "extra_log_text"
        var isServiceRunning = false
    }

    private var pressStartTime = 0L
    private var lastUpTime = 0L
    private var tapCount = 0
    private val longPressThreshold = 600L   // 0.6초 이상 누르면 "꾹 누르기 (홈)"
    private val doubleTapInterval = 350L    // 0.35초 내에 다시 누르면 연속 탭(까닥 취소)

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        broadcastLog("브릿지 서비스 연결됨: 제스처 감지 준비 완료", -1)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isServiceRunning = false
        broadcastLog("브릿지 서비스 해제됨", -1)
        return super.onUnbind(intent)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!BridgeConfig.isBridgeEnabled(this)) {
            return super.onKeyEvent(event)
        }

        val keyCode = event.keyCode
        val action = event.action

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || 
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || 
            keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || 
            keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || 
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
            keyCode == KeyEvent.KEYCODE_HEADSETHOOK) {

            val now = System.currentTimeMillis()

            if (action == KeyEvent.ACTION_DOWN) {
                if (event.repeatCount == 0) {
                    pressStartTime = now
                }
            } else if (action == KeyEvent.ACTION_UP) {
                val duration = now - pressStartTime

                when {
                    // 1. [꾹 누르기] (0.6초 이상 길게 유지) -> 홈 화면 / 메인 진입
                    duration >= longPressThreshold -> {
                        vibratePattern(long = true)
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        broadcastLog("제스처 감지: [꾹 누르기] -> 홈 화면 이동", keyCode)
                    }

                    // 2. [까닥 취소 / 더블 탭] (빠른 두 번 까닥) -> 취소 / 뒤로가기
                    (now - lastUpTime) <= doubleTapInterval -> {
                        tapCount++
                        if (tapCount >= 2) {
                            vibratePattern(long = false)
                            performGlobalAction(GLOBAL_ACTION_BACK) // 취소 / 뒤로가기
                            broadcastLog("제스처 감지: [까닥 취소] -> 뒤로가기 실행", keyCode)
                            tapCount = 0
                        }
                    }

                    // 3. [손가락 까닥 / 단일 탭] -> 다음 항목 이동 또는 선택
                    else -> {
                        tapCount = 1
                        vibratePattern(long = false)
                        handleNavigationOrSelection()
                        broadcastLog("제스처 감지: [손가락 까닥/탭] -> 항목 이동/선택", keyCode)
                    }
                }
                lastUpTime = now
            }
            return true
        }

        return super.onKeyEvent(event)
    }

    private fun handleNavigationOrSelection() {
        val root = rootInActiveWindow
        if (root != null) {
            val nodes = mutableListOf<AccessibilityNodeInfo>()
            collectFocusableNodes(root, nodes)

            if (nodes.isNotEmpty()) {
                val currentIndex = nodes.indexOfFirst { it.isAccessibilityFocused || it.isFocused }
                val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % nodes.size
                nodes[nextIndex].performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                nodes[nextIndex].performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                return
            }
        }
        performCenterClick()
    }

    private fun performCenterClick() {
        val metrics = resources.displayMetrics
        val path = Path().apply {
            moveTo(metrics.widthPixels / 2f, metrics.heightPixels / 2f)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun vibratePattern(long: Boolean) {
        if (!BridgeConfig.isVibrateEnabled(this)) return
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        val duration = if (long) 70L else 20L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    private fun collectFocusableNodes(node: AccessibilityNodeInfo?, list: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        if (node.isVisibleToUser && (node.isClickable || node.isFocusable)) {
            list.add(node)
        }
        for (i in 0 until node.childCount) {
            collectFocusableNodes(node.getChild(i), list)
        }
    }

    private fun broadcastLog(message: String, keyCode: Int) {
        BridgeConfig.saveLastLog(this, message)
        val intent = Intent(ACTION_KEY_LOG).apply {
            setPackage(packageName)
            putExtra(EXTRA_LOG_TEXT, message)
        }
        sendBroadcast(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
