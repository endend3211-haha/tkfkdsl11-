package com.example.r08memobridge

accessibilityservice.AccessibilityService
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isServiceRunning = false
        return super.onUnbind(intent)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!BridgeConfig.isBridgeEnabled(this)) {
            return super.onKeyEvent(event)
        }

        val keyCode = event.keyCode
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || 
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || 
            keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || 
            keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || 
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
            keyCode == KeyEvent.KEYCODE_HEADSETHOOK) {

            if (event.action == KeyEvent.ACTION_DOWN) {
                if (event.repeatCount == 0) {
                    pressStartTime = System.currentTimeMillis()
                }
            } else if (event.action == KeyEvent.ACTION_UP) {
                val duration = System.currentTimeMillis() - pressStartTime

                if (duration >= 600L) {
                    // 꾹 누르기 -> 홈 화면 이동
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    broadcastLog("제스처: [꾹 누르기] -> 홈 이동", keyCode)
                } else {
                    // 일반 클릭/까닥 -> 다음 포커스 이동 및 선택
                    handleNavigationOrSelection()
                    broadcastLog("제스처: [까닥/탭] -> 항목 이동", keyCode)
                }
                vibrateShort()
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
                val nextIndex = 0
                nodes[nextIndex].performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                nodes[nextIndex].performAction(AccessibilityNodeInfo.ACTION_CLICK)
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

    private fun vibrateShort() {
        if (!BridgeConfig.isVibrateEnabled(this)) return
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30)
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
