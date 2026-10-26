package com.jingchuang.sweep

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.jingchuang.sweep.core.MatchKind
import com.jingchuang.sweep.core.Rule
import com.jingchuang.sweep.core.RuleAction
import com.jingchuang.sweep.core.RuleStore
import com.jingchuang.sweep.service.SweepService
import com.jingchuang.sweep.util.Prefs
import com.jingchuang.sweep.util.Ui

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var dot: TextView
    private lateinit var clicksText: TextView
    private lateinit var masterSwitch: SwitchCompat
    private lateinit var btnEnable: Button
    private lateinit var ruleBox: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        dot = findViewById(R.id.dot)
        clicksText = findViewById(R.id.clicksText)
        masterSwitch = findViewById(R.id.masterSwitch)
        btnEnable = findViewById(R.id.btnEnable)
        ruleBox = findViewById(R.id.ruleBox)

        btnEnable.setOnClickListener { openAccessibilitySettings() }
        findViewById<Button>(R.id.btnApps).setOnClickListener {
            startActivity(Intent(this, AppsActivity::class.java))
        }
        findViewById<Button>(R.id.btnLearn).setOnClickListener {
            startActivity(Intent(this, LearnActivity::class.java))
        }
        findViewById<Button>(R.id.btnAddGlobal).setOnClickListener {
            showAddRuleDialog("")
        }
        masterSwitch.setOnCheckedChangeListener { _, checked ->
            Prefs.setEnabled(this, checked)
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun openAccessibilitySettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }.onFailure {
            Toast.makeText(this, R.string.toast_no_settings, Toast.LENGTH_SHORT).show()
        }
    }

    private fun isServiceOn(): Boolean {
        val am = getSystemService(AccessibilityManager::class.java) ?: return false
        val want = ComponentName(this, SweepService::class.java)
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val si = info.resolveInfo?.serviceInfo ?: return@any false
                si.packageName == want.packageName && si.name == want.className
            }
    }

    private fun refresh() {
        val on = isServiceOn()
        val master = Prefs.enabled(this)

        dot.setTextColor(ContextCompat.getColor(this, if (on && master) R.color.good else R.color.off))
        statusText.text = when {
            !master -> getString(R.string.status_master_off)
            on -> getString(R.string.status_on)
            else -> getString(R.string.status_off)
        }
        clicksText.text = getString(R.string.clicks_fmt, Prefs.totalClicks(this))
        masterSwitch.isChecked = master
        btnEnable.isEnabled = !on
        btnEnable.text = if (on) getString(R.string.btn_service_on) else getString(R.string.btn_enable_service)

        renderRules()
    }

    private fun renderRules() {
        ruleBox.removeAllViews()
        val rules = RuleStore.load(this)
            .sortedWith(compareBy({ !it.isGlobal }, { it.pkg }))

        if (rules.isEmpty()) {
            ruleBox.addView(Ui.gray(this, 13f).apply {
                text = getString(R.string.no_rules)
                setPadding(0, Ui.dp(this@MainActivity, 6f), 0, 0)
            })
            return
        }
        rules.forEach { ruleBox.addView(ruleRow(it)) }
    }

    private fun ruleRow(rule: Rule): View {
        val row = Ui.horizontal(this)
        row.setPadding(0, Ui.dp(this, 11f), 0, Ui.dp(this, 11f))

        val tag = Ui.ink(this, 11f, true).apply {
            text = if (rule.isGlobal) getString(R.string.tag_global) else appLabel(rule.pkg)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent))
        }
        row.addView(tag, LinearLayout.LayoutParams(Ui.dp(this, 74f), LinearLayout.LayoutParams.WRAP_CONTENT))

        val title = Ui.ink(this, 13f).apply {
            text = "${Ui.kindName(rule.kind)}「${rule.value}」→ ${Ui.actionName(rule.action)}"
            maxLines = 2
        }
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lp.marginEnd = Ui.dp(this, 8f)
        row.addView(title, lp)

        val sw = SwitchCompat(this).apply {
            isChecked = rule.enabled
            setOnCheckedChangeListener { _, checked ->
                RuleStore.setEnabled(this@MainActivity, rule.id, checked)
            }
        }
        row.addView(sw)

        row.setOnLongClickListener {
            confirmDelete(rule)
            true
        }
        return row
    }

    private fun confirmDelete(rule: Rule) {
        AlertDialog.Builder(this)
            .setTitle(R.string.dlg_delete_title)
            .setMessage(getString(R.string.dlg_delete_msg, rule.value))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dlg_delete_yes) { _, _ ->
                RuleStore.delete(this, rule.id)
                refresh()
            }
            .show()
    }

    private fun showAddRuleDialog(pkg: String) {
        val box = Ui.vertical(this)
        val pad = Ui.dp(this, 20f)
        box.setPadding(pad, Ui.dp(this, 8f), pad, 0)

        val valueEt = EditText(this).apply {
            hint = getString(R.string.dlg_value_hint)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        box.addView(valueEt, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val kindSp = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity, android.R.layout.simple_spinner_item, Ui.allKinds()
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(1)
        }
        box.addView(kindSp, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val actSp = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity, android.R.layout.simple_spinner_item, Ui.allActions()
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(1)
        }
        box.addView(actSp, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        AlertDialog.Builder(this)
            .setTitle(
                if (pkg.isEmpty()) getString(R.string.dlg_add_global)
                else getString(R.string.dlg_add_for, appLabel(pkg))
            )
            .setView(box)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dlg_save) { _, _ ->
                val v = valueEt.text.toString().trim()
                if (v.isEmpty()) {
                    Toast.makeText(this, R.string.toast_value_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val kind = MatchKind.values()[kindSp.selectedItemPosition]
                val act = RuleAction.values()[actSp.selectedItemPosition]
                RuleStore.add(this, Rule.make(pkg = pkg, kind = kind, value = v, action = act))
                refresh()
            }
            .show()
    }

    private fun appLabel(pkg: String): String {
        if (pkg.isEmpty()) return getString(R.string.tag_global)
        return runCatching {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(ai).toString()
        }.getOrDefault(pkg)
    }
}
