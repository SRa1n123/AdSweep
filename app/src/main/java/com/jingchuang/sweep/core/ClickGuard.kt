package com.jingchuang.sweep.core

import android.util.Log
import org.json.JSONArray

/**
 * 规则安全闸：宁可不点，也绝不替用户点「同意 / 支付 / 安装」这类按钮。
 */
object ClickGuard {

    private const val TAG = "ClickGuard"

    /** 完全命中即拒绝（广告弹窗里不会有这些词，但正常弹窗里会有） */
    private val DENY_EXACT = setOf(
        "确定", "确认", "同意", "允许", "安装", "立即安装", "更新", "立即更新",
        "支付", "立即支付", "购买", "立即购买", "登录", "授权", "删除", "注销",
        "解绑", "退出登录", "立即体验", "开始使用", "获取验证码", "继续安装"
    )

    /** 包含即拒绝 */
    private val DENY_CONTAINS = listOf(
        "同意并", "确认支付", "确认购买", "允许访问", "授权登录", "立即开通", "开通会员"
    )

    /**
     * @param text     控件文本（可能是空串，比如纯图标的 ×）
     * @param editable 该控件或祖先是否为输入框
     */
    fun allowed(text: String, editable: Boolean): Boolean {
        if (editable) return false
        val t = text.trim()
        if (t.isEmpty()) return true                 // 无文字图标（×/✕）放行，由规则本身限定
        if (t.length > 12) return false              // 长文案一律不点，避免误触正文
        if (DENY_EXACT.any { it == t }) {
            Log.d(TAG, "deny exact: $t")
            return false
        }
        if (DENY_CONTAINS.any { t.contains(it) }) {
            Log.d(TAG, "deny contains: $t")
            return false
        }
        return true
    }

    fun isJsonArray(s: String): Boolean = runCatching {
        val a = JSONArray(s); a.length() >= 0
    }.getOrDefault(false)
}
