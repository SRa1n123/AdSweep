package com.jingchuang.sweep

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.jingchuang.sweep.util.Prefs
import com.jingchuang.sweep.util.Ui

class AppsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps)

        val list = findViewById<LinearLayout>(R.id.appList)
        val pm = packageManager

        val infos = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )
            .mapNotNull { it.activityInfo?.applicationInfo }
            .filter { it.packageName != packageName }
            .distinctBy { it.packageName }
            .sortedBy { pm.getApplicationLabel(it).toString() }

        if (infos.isEmpty()) {
            list.addView(Ui.gray(this, 13f).apply { text = getString(R.string.apps_empty) })
            return
        }

        for (ai in infos) {
            val pkg = ai.packageName
            val label = pm.getApplicationLabel(ai).toString()

            val row = Ui.horizontal(this)
            row.setPadding(0, Ui.dp(this, 12f), 0, Ui.dp(this, 12f))

            val tv = Ui.ink(this, 15f).apply { text = label }
            row.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            val sw = SwitchCompat(this).apply {
                isChecked = Prefs.pkgEnabled(this@AppsActivity, pkg)
                setOnCheckedChangeListener { _, checked ->
                    Prefs.setPkgEnabled(this@AppsActivity, pkg, checked)
                }
            }
            row.addView(sw)
            list.addView(row)
        }
    }
}
