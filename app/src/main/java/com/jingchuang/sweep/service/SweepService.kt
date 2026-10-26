package com.jingchuang.sweep.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.jingchuang.sweep.core.ClickGuard
import com.jingchuang.sweep.core.NodeLite
import com.jingchuang.sweep.core.NodeLiteFactory
import com.jingchuang.sweep.core.NodeScanner
import com.jingchuang.sweep.core.Rule
import com.jingchuang.sweep.core.RuleAction
import com.jingchuang.sweep.core.RuleStore
import com.jingchuang.sweep.util.Prefs

/**
 * 核心引擎。
 *
 * 只做三件事：
 *  1. 窗口变化 → 起一段「爆发期」，在 6 秒内高频扫描（广告弹窗通常在这段时间出现）
 *  2. 扫描所有窗口的节点树（弹窗常是独立 window，只看 focused root 会漏）
 *  3. 命中规则 → 点击；节点不可点则退回手势点击中心点
 *
 * 安全：每次窗口最多点 3 下、间隔 ≥1.1s、永不点「同意/支付/安装」类文本、不碰输入框。
 */
class SweepService : AccessibilityService() {

    companion object {
        private const val TAG = "SweepService"
        private const val BURST_MS = 6000L
        private const val COOLDOWN_MS = 1100L
        private const val MAX_CLICKS = 3

        @Volatile
        var instance: SweepService? = null
            private set

        fun isConnected(): Boolean = instance != null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var rules: List<Rule> = emptyList()
    private var burstUntil = 0L
    private var lastClickAt = 0L
    private var clicksInWindow = 0
    private var windowToken = ""

    /** 最近一次的界面快照，给「学习模式」兜底用（广告已经关掉也能补学规则） */
    @Volatile
    var lastPkg: String = ""
        private set

    @Volatile
    var lastNodes: List<NodeLite> = emptyList()
        private set

    private val scanTask = Runnable { scan() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        rules = RuleStore.load(this)
        Log.i(TAG, "connected, rules=${rules.size}")
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        if (!Prefs.enabled(this)) return

        val pkg = e.packageName?.toString().orEmpty()
        if (pkg.isEmpty() || pkg == applicationContext.packageName) return
        if (!Prefs.pkgEnabled(this, pkg)) return

        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                if (pkg != windowToken) {
                    windowToken = pkg
                    clicksInWindow = 0
                }
                rules = RuleStore.load(this)
                burstUntil = System.currentTimeMillis() + BURST_MS
                cacheSnapshot()
                scheduleBurst()
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (System.currentTimeMillis() < burstUntil) scheduleBurst()
            }
            else -> Unit
        }
    }

    /**
     * 给「学习模式」抓一份最近的界面快照（只存文本/id/描述，不持有节点引用）。
     */
    private fun cacheSnapshot() {
        runCatching {
            for (root in collectRoots()) {
                val pkg = root.packageName?.toString().orEmpty()
                if (pkg.isEmpty() || pkg == applicationContext.packageName) continue
                lastPkg = pkg
                lastNodes = NodeScanner.collect(root, 400).map { NodeLiteFactory.of(it) }
                return
            }
        }
    }

    private fun scheduleBurst() {
        handler.removeCallbacks(scanTask)
        val offsets = longArrayOf(60, 260, 620, 1100, 1800, 2800)
        offsets.forEach { d -> handler.postDelayed(scanTask, d) }
    }

    private fun scan() {
        if (!Prefs.enabled(this)) return
        val now = System.currentTimeMillis()
        if (now > burstUntil) return
        if (now - lastClickAt < COOLDOWN_MS) return
        if (clicksInWindow >= MAX_CLICKS) return
        if (rules.isEmpty()) rules = RuleStore.load(this)

        val roots = collectRoots()
        if (roots.isEmpty()) return

        for (root in roots) {
            val nodes = NodeScanner.collect(root, 1500)
            for (n in nodes) {
                val pkg = n.packageName?.toString().orEmpty()
                if (pkg.isEmpty() || pkg == applicationContext.packageName) continue
                if (!Prefs.pkgEnabled(this, pkg)) continue

                val text = NodeScanner.textOf(n)
                if (!ClickGuard.allowed(text, NodeScanner.editableChain(n))) continue

                for (r in rules) {
                    if (!r.enabled) continue
                    if (r.pkg.isNotEmpty() && r.pkg != pkg) continue
                    if (!NodeScanner.matches(r, n)) continue
                    if (fire(n, r)) {
                        Log.i(TAG, "hit kind=${r.kind} value='${r.value}' pkg=$pkg text='$text'")
                        return
                    }
                }
            }
        }
    }

    /** 所有可见窗口的 root（弹窗通常是独立的 application window） */
    private fun collectRoots(): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>(4)
        runCatching {
            val ws: List<AccessibilityWindowInfo> = windows ?: emptyList()
            for (w in ws) {
                val t = runCatching { w.type }.getOrDefault(AccessibilityWindowInfo.TYPE_APPLICATION)
                if (t != AccessibilityWindowInfo.TYPE_APPLICATION &&
                    t != AccessibilityWindowInfo.TYPE_SYSTEM
                ) continue
                val r = runCatching { w.root }.getOrNull() ?: continue
                out.add(r)
            }
        }
        if (out.isEmpty()) {
            runCatching { rootInActiveWindow }.getOrNull()?.let { out.add(it) }
        }
        return out
    }

    private fun fire(n: AccessibilityNodeInfo, r: Rule): Boolean {
        val ok = when (r.action) {
            RuleAction.CLICK_SELF -> runCatching { n.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
                .getOrDefault(false) || tap(n)

            RuleAction.CLICK_PARENT -> {
                val c = NodeScanner.clickableAncestor(n)
                val clicked = c != null && runCatching {
                    c.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }.getOrDefault(false)
                clicked || tap(n)
            }

            RuleAction.TAP_CENTER -> tap(n)

            RuleAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
        }
        if (ok) {
            lastClickAt = System.currentTimeMillis()
            clicksInWindow++
            Prefs.addClicks(this)
            RuleStore.bump(this, r.id)
        }
        return ok
    }

    private fun tap(n: AccessibilityNodeInfo): Boolean {
        val r = Rect()
        runCatching { n.getBoundsInScreen(r) }
        if (r.width() <= 0 || r.height() <= 0) return false
        val path = Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        return runCatching { dispatchGesture(g, null, null) }.getOrDefault(false)
    }
}
