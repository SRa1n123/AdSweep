package com.jingchuang.sweep.core

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 节点树遍历与匹配。所有方法都不持有节点引用，避免泄漏。
 */
object NodeScanner {

    private val cache = HashMap<String, Regex>()
    private const val REGEX_CACHE_MAX = 64

    fun regex(pattern: String): Regex? = synchronized(cache) {
        cache[pattern] ?: runCatching { Regex(pattern) }.getOrNull()?.also {
            if (cache.size > REGEX_CACHE_MAX) cache.clear()
            cache[pattern] = it
        }
    }

    fun textOf(n: AccessibilityNodeInfo): String =
        (n.text ?: n.contentDescription ?: "").toString().trim()

    fun descOf(n: AccessibilityNodeInfo): String =
        (n.contentDescription ?: "").toString().trim()

    fun idOf(n: AccessibilityNodeInfo): String = n.viewIdResourceName ?: ""

    /** 广度优先收集整棵子树，最多 limit 个节点 */
    fun collect(root: AccessibilityNodeInfo, limit: Int = 1500): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>(64)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty() && out.size < limit) {
            val n = queue.removeFirst()
            out.add(n)
            for (i in 0 until n.childCount) {
                val c = runCatching { n.getChild(i) }.getOrNull() ?: continue
                queue.add(c)
            }
        }
        return out
    }

    fun bounds(n: AccessibilityNodeInfo): Rect = Rect().also { n.getBoundsInScreen(it) }

    /** 往上找第一个「可点击且不是输入框」的祖先（含自己） */
    fun clickableAncestor(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var cur = n
        var guard = 0
        while (cur != null && guard++ < 8) {
            if (cur.isClickable && !cur.isEditable) return cur
            cur = runCatching { cur.parent }.getOrNull()
        }
        return null
    }

    fun editableChain(n: AccessibilityNodeInfo?): Boolean {
        var cur = n
        var guard = 0
        while (cur != null && guard++ < 8) {
            if (cur.isEditable) return true
            cur = runCatching { cur.parent }.getOrNull()
        }
        return false
    }

    fun visible(n: AccessibilityNodeInfo): Boolean = runCatching { n.isVisibleToUser }.getOrDefault(true)

    /**
     * 规则匹配。要求：文本类规则必须有非空文本；ID 类规则必须有 viewId。
     */
    fun matches(r: Rule, n: AccessibilityNodeInfo): Boolean {
        if (!visible(n)) return false
        if (n.packageName?.toString().orEmpty().isEmpty()) return false
        return when (r.kind) {
            MatchKind.TEXT_EQ -> {
                val t = textOf(n)
                t.isNotEmpty() && t == r.value
            }
            MatchKind.TEXT_CONTAINS -> {
                val t = textOf(n)
                t.isNotEmpty() && t.contains(r.value)
            }
            MatchKind.TEXT_REGEX -> {
                val t = textOf(n)
                t.isNotEmpty() && regex(r.value)?.containsMatchIn(t) == true
            }
            MatchKind.DESC_CONTAINS -> {
                val d = descOf(n)
                d.isNotEmpty() && d.contains(r.value)
            }
            MatchKind.ID_REGEX -> {
                val id = idOf(n)
                id.isNotEmpty() && regex(r.value)?.containsMatchIn(id) == true
            }
        }
    }
}
