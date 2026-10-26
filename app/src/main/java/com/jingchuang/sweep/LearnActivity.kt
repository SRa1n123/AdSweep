package com.jingchuang.sweep

import android.os.Bundle
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.jingchuang.sweep.core.MatchKind
import com.jingchuang.sweep.core.NodeLite
import com.jingchuang.sweep.core.NodeLiteFactory
import com.jingchuang.sweep.core.NodeScanner
import com.jingchuang.sweep.core.Rule
import com.jingchuang.sweep.core.RuleAction
import com.jingchuang.sweep.core.RuleStore
import com.jingchuang.sweep.service.SweepService
import com.jingchuang.sweep.util.Ui

/**
 * 学习模式：抓一份「那个 App」的界面节点，让你直接指哪个是关闭按钮，
 * 自动生成一条只对这个 App 生效的规则。
 *
 * 优先抓实时界面；广告已经关掉时，退回服务缓存的最近一次快照。
 */
class LearnActivity : AppCompatActivity() {

    private lateinit var header: TextView
    private lateinit var hint: TextView
    private lateinit var nodeList: LinearLayout

    private var targetPkg: String = ""
    private var items: List<NodeLite> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_learn)

        header = findViewById(R.id.learnHeader)
        hint = findViewById(R.id.learnHint)
        nodeList = findViewById(R.id.nodeList)

        findViewById<Button>(R.id.btnRefresh).setOnClickListener { refreshSnapshot() }
        refreshSnapshot()
    }

    override fun onResume() {
        super.onResume()
        refreshSnapshot()
    }

    private fun refreshSnapshot() {
        nodeList.removeAllViews()

        val svc = SweepService.instance
        if (svc == null) {
            header.text = ""
            hint.text = getString(R.string.learn_service_off)
            return
        }

        // 1) 实时抓那个「不是我们自己的」应用窗口
        var rootPkg = ""
        var lite: List<NodeLite> = emptyList()
        for (w in (svc.windows ?: emptyList())) {
            if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val r = w.root ?: continue
            val p = r.packageName?.toString().orEmpty()
            if (p.isEmpty() || p == packageName) continue
            rootPkg = p
            lite = NodeScanner.collect(r, 400).map { NodeLiteFactory.of(it) }
            break
        }

        // 2) 兜底：用服务缓存的最近一次快照
        if (lite.isEmpty() && svc.lastNodes.isNotEmpty()) {
            rootPkg = svc.lastPkg
            lite = svc.lastNodes
        }

        if (lite.isEmpty()) {
            header.text = ""
            hint.text = getString(R.string.learn_no_target)
            return
        }

        targetPkg = rootPkg
        items = lite
        header.text = getString(R.string.learn_header, appLabel(targetPkg), items.size)
        hint.text = getString(R.string.learn_hint)
        renderNodes()
    }

    private fun renderNodes() {
        val shown = items.filter { it.hasAny }

        if (shown.isEmpty()) {
            nodeList.addView(Ui.gray(this, 13f).apply { text = getString(R.string.learn_empty) })
            return
        }

        for (item in shown) {
            val row = Ui.vertical(this)
            row.setPadding(0, Ui.dp(this, 10f), 0, Ui.dp(this, 10f))

            val label = Ui.ink(this, 14f).apply {
                text = (item.text.ifEmpty { item.desc }).ifEmpty { getString(R.string.learn_no_text) }
                maxLines = 2
            }
            row.addView(label)

            val meta = buildString {
                if (item.id.isNotEmpty()) append(item.id.substringAfterLast('/')).append("  ")
                append(item.cls)
                if (item.clickable) append("  [可点]")
            }
            row.addView(Ui.gray(this, 11f).apply { text = meta })

            row.setOnClickListener { pick(item) }
            nodeList.addView(row)
        }
    }

    private fun pick(item: NodeLite) {
        val kinds = ArrayList<MatchKind>()
        val values = ArrayList<String>()

        if (item.text.isNotEmpty()) {
            kinds.add(MatchKind.TEXT_CONTAINS); values.add(item.text)
            kinds.add(MatchKind.TEXT_EQ); values.add(item.text)
            kinds.add(MatchKind.TEXT_REGEX); values.add(escapeRegex(item.text))
        }
        if (item.id.isNotEmpty()) {
            kinds.add(MatchKind.ID_REGEX); values.add("^" + escapeRegex(item.id) + "$")
        }
        if (item.desc.isNotEmpty()) {
            kinds.add(MatchKind.DESC_CONTAINS); values.add(item.desc)
        }

        if (kinds.isEmpty()) {
            Toast.makeText(this, R.string.learn_nothing_to_bind, Toast.LENGTH_SHORT).show()
            return
        }

        val box = Ui.vertical(this)
        val pad = Ui.dp(this, 20f)
        box.setPadding(pad, Ui.dp(this, 8f), pad, 0)

        val valueEt = EditText(this).apply { setText(values[0]) }
        box.addView(valueEt, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val kindSp = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@LearnActivity,
                android.R.layout.simple_spinner_item,
                kinds.map { Ui.kindName(it) }
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        }
        box.addView(kindSp, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val actSp = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@LearnActivity, android.R.layout.simple_spinner_item, Ui.allActions()
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(1)
        }
        box.addView(actSp, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        AlertDialog.Builder(this)
            .setTitle(R.string.learn_pick_title)
            .setMessage(getString(R.string.learn_pick_msg, appLabel(targetPkg)))
            .setView(box)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dlg_save) { _, _ ->
                val v = valueEt.text.toString().trim()
                if (v.isEmpty()) {
                    Toast.makeText(this, R.string.toast_value_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val kind = kinds[kindSp.selectedItemPosition]
                val act = RuleAction.values()[actSp.selectedItemPosition]
                RuleStore.add(this, Rule.make(pkg = targetPkg, kind = kind, value = v, action = act))
                Toast.makeText(this, R.string.learn_saved, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun escapeRegex(s: String): String = Regex.escape(s)

    private fun appLabel(pkg: String): String {
        if (pkg.isEmpty()) return pkg
        return runCatching {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(ai).toString()
        }.getOrDefault(pkg)
    }
}
