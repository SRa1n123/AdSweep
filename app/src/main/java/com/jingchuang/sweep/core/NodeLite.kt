package com.jingchuang.sweep.core

import android.view.accessibility.AccessibilityNodeInfo

/**
 * 节点的「轻量快照」：只留建规则需要的字段，不持有 AccessibilityNodeInfo 引用。
 *
 * 这样「学习模式」即使广告已经关掉、节点已经失效，也能从服务缓存里补学规则。
 */
data class NodeLite(
    val text: String = "",
    val desc: String = "",
    val id: String = "",
    val cls: String = "",
    val clickable: Boolean = false
) {
    val hasAny: Boolean
        get() = text.isNotEmpty() || desc.isNotEmpty() || id.isNotEmpty() || clickable
}

object NodeLiteFactory {
    fun of(n: AccessibilityNodeInfo): NodeLite = NodeLite(
        text = (n.text ?: "").toString().trim(),
        desc = (n.contentDescription ?: "").toString().trim(),
        id = n.viewIdResourceName ?: "",
        cls = n.className?.toString()?.substringAfterLast('.') ?: "",
        clickable = n.isClickable
    )
}
