package com.example.r08memobridge

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class RingBridgeService : AccessibilityService() {

    companion object {
        const val ACTION_KEY_LOG = "com.example.r08memobridge.ACTION_KEY_LOG"
        const val EXTRA_LOG_TEXT = "extra_log_text"
        const val EXTRA_KEY_CODE = "extra_key_code"
        var isServiceRunning = false
        var learningTarget: String? = null // "UP", "DOWN", "SELECT", "BACK" or null
    }

    private var lastDownTime = 0L
    private var lastKeyCode = -1
    private val doubleTapThreshold = 350L

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        broadcastLog("브릿지 접근성 서비스가 연결되었습니다.", -1)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isServiceRunning = false
        broadcastLog("브릿지 접근성 서비스가 해제되었습니다.", -1)
        return super.onUnbind(intent)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val keyName = KeyEvent.keyCodeToString(keyCode)

        // 키 학습 모드인 경우
        if (learningTarget != null && event.action == KeyEvent.ACTION_DOWN) {
            when (learningTarget) {
                "UP" -> BridgeConfig.setUpKeyCode(this, keyCode)
                "DOWN" -> BridgeConfig.setDownKeyCode(this, keyCode)
                "SELECT" -> BridgeConfig.setSelectKeyCode(this, keyCode)
                "BACK" -> BridgeConfig.setBackKeyCode(this, keyCode)
            }
            val targetName = learningTarget
            learningTarget = null
            vibrateShort()
            broadcastLog("[$targetName] 매핑 완료: $keyName ($keyCode)", keyCode)
            return true
        }

        if (!BridgeConfig.isBridgeEnabled(this)) {
            return super.onKeyEvent(event)
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            val now = System.currentTimeMillis()
            val upKey = BridgeConfig.getUpKeyCode(this)
            val downKey = BridgeConfig.getDownKeyCode(this)
            val selectKey = BridgeConfig.getSelectKeyCode(this)
            val backKey = BridgeConfig.getBackKeyCode(this)

            // 1. 매핑된 키와 일치하는지 확인 (보조 미디어 키도 기본 지원)
            when {
                keyCode == upKey || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    vibrateShort()
                    handleNavigation(forward = false)
                    broadcastLog("입력: $keyName ($keyCode) -> [이전 메뉴 이동]", keyCode)
                    return true
                }
                keyCode == downKey || (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT && backKey != KeyEvent.KEYCODE_MEDIA_NEXT) -> {
                    vibrateShort()
                    handleNavigation(forward = true)
                    broadcastLog("입력: $keyName ($keyCode) -> [다음 메뉴 이동]", keyCode)
                    return true
                }
                keyCode == selectKey || keyCode == KeyEvent.KEYCODE_HEADSETHOOK || keyCode == KeyEvent.KEYCODE_ENTER -> {
                    vibrateShort()
                    // 더블 탭 감지 시 뒤로가기 수행 옵션
                    if (lastKeyCode == keyCode && (now - lastDownTime) < doubleTapThreshold) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        broadcastLog("입력: $keyName 더블탭 -> [뒤로가기]", keyCode)
                        lastKeyCode = -1
                    } else {
                        handleSelection()
                        broadcastLog("입력: $keyName ($keyCode) -> [메뉴 선택/실행]", keyCode)
                        lastKeyCode = keyCode
                        lastDownTime = now
                    }
                    return true
                }
                keyCode == backKey -> {
                    vibrateShort()
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    broadcastLog("입력: $keyName ($keyCode) -> [뒤로가기]", keyCode)
                    return true
                }
                else -> {
                    broadcastLog("감지된 미매핑 키: $keyName ($keyCode)", keyCode)
                }
            }
        }
        return super.onKeyEvent(event)
    }

    private fun handleNavigation(forward: Boolean) {
        val navMode = BridgeConfig.getNavMode(this)
        when (navMode) {
            0 -> { // 포커스 이동 우선 + 실패 시 스와이프
                if (!moveNodeFocus(forward)) {
                    performSwipeGesture(downward = forward)
                }
            }
            1 -> { // 스와이프 전용 모드 (대시보드/프롬프터 스크롤)
                performSwipeGesture(downward = forward)
            }
            2 -> { // 포커스 이동 전용 모드
                moveNodeFocus(forward)
            }
        }
    }

    private fun moveNodeFocus(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val clickableNodes = mutableListOf<AccessibilityNodeInfo>()
        collectFocusableNodes(root, clickableNodes)

        if (clickableNodes.isEmpty()) return false

        var currentIndex = clickableNodes.indexOfFirst {
            it.isAccessibilityFocused || it.isFocused
        }

        val nextIndex = if (currentIndex == -1) {
            if (forward) 0 else clickableNodes.lastIndex
        } else {
            if (forward) (currentIndex + 1) % clickableNodes.size
            else (currentIndex - 1 + clickableNodes.size) % clickableNodes.size
        }

        val targetNode = clickableNodes[nextIndex]
        val focused = targetNode.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return focused
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

    private fun handleSelection() {
        val root = rootInActiveWindow
        if (root != null) {
            val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
                ?: root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focusedNode != null && focusedNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return
            }
        }
        // 포커스된 노드가 없을 경우 화면 중앙 탭 실행
        performCenterTap()
    }

    private fun performSwipeGesture(downward: Boolean) {
        val metrics = resources.displayMetrics
        val centerX = metrics.widthPixels / 2f
        val startY = if (downward) metrics.heightPixels * 0.72f else metrics.heightPixels * 0.28f
        val endY = if (downward) metrics.heightPixels * 0.28f else metrics.heightPixels * 0.72f

        val path = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, 180)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun performCenterTap() {
        val metrics = resources.displayMetrics
        val centerX = metrics.widthPixels / 2f
        val centerY = metrics.heightPixels / 2f

        val path = Path().apply {
            moveTo(centerX, centerY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, 60)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun vibrateShort() {
        if (!BridgeConfig.isVibrateEnabled(this)) return
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(25)
        }
    }

    private fun broadcastLog(message: String, keyCode: Int) {
        BridgeConfig.saveLastLog(this, message)
        val intent = Intent(ACTION_KEY_LOG).apply {
            setPackage(packageName)
            putExtra(EXTRA_LOG_TEXT, message)
            putExtra(EXTRA_KEY_CODE, keyCode)
        }
        sendBroadcast(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
