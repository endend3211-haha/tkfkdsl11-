package com.example.r08memobridge

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent

object BridgeConfig {
    private const val PREF_NAME = "r08_bridge_prefs"
    private const val KEY_BRIDGE_ENABLED = "bridge_enabled"
    private const val KEY_NAV_MODE = "nav_mode" // 0: Focus+Swipe, 1: Swipe Only, 2: Focus Only
    private const val KEY_VIBRATE_FEEDBACK = "vibrate_feedback"
    private const val KEY_LAST_LOG = "last_log"

    private const val KEY_MAP_UP = "map_up"
    private const val KEY_MAP_DOWN = "map_down"
    private const val KEY_MAP_SELECT = "map_select"
    private const val KEY_MAP_BACK = "map_back"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isBridgeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BRIDGE_ENABLED, true)

    fun setBridgeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BRIDGE_ENABLED, enabled).apply()
    }

    fun getNavMode(context: Context): Int =
        prefs(context).getInt(KEY_NAV_MODE, 0)

    fun setNavMode(context: Context, mode: Int) {
        prefs(context).edit().putInt(KEY_NAV_MODE, mode).apply()
    }

    fun isVibrateEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VIBRATE_FEEDBACK, true)

    fun setVibrateEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_VIBRATE_FEEDBACK, enabled).apply()
    }

    fun getUpKeyCode(context: Context): Int =
        prefs(context).getInt(KEY_MAP_UP, KeyEvent.KEYCODE_VOLUME_UP)

    fun setUpKeyCode(context: Context, keyCode: Int) {
        prefs(context).edit().putInt(KEY_MAP_UP, keyCode).apply()
    }

    fun getDownKeyCode(context: Context): Int =
        prefs(context).getInt(KEY_MAP_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN)

    fun setDownKeyCode(context: Context, keyCode: Int) {
        prefs(context).edit().putInt(KEY_MAP_DOWN, keyCode).apply()
    }

    fun getSelectKeyCode(context: Context): Int =
        prefs(context).getInt(KEY_MAP_SELECT, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)

    fun setSelectKeyCode(context: Context, keyCode: Int) {
        prefs(context).edit().putInt(KEY_MAP_SELECT, keyCode).apply()
    }

    fun getBackKeyCode(context: Context): Int =
        prefs(context).getInt(KEY_MAP_BACK, KeyEvent.KEYCODE_MEDIA_NEXT)

    fun setBackKeyCode(context: Context, keyCode: Int) {
        prefs(context).edit().putInt(KEY_MAP_BACK, keyCode).apply()
    }

    fun saveLastLog(context: Context, log: String) {
        prefs(context).edit().putString(KEY_LAST_LOG, log).apply()
    }

    fun getLastLog(context: Context): String =
        prefs(context).getString(KEY_LAST_LOG, "수신된 키 이벤트 없음") ?: "수신된 키 이벤트 없음"
}
