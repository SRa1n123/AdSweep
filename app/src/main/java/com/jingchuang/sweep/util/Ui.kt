package com.jingchuang.sweep.util

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.jingchuang.sweep.R
import com.jingchuang.sweep.core.MatchKind
import com.jingchuang.sweep.core.RuleAction

object Ui {

    fun dp(ctx: Context, v: Float): Int =
        (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun vertical(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
    }

    fun horizontal(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun ink(ctx: Context, size: Float = 15f, bold: Boolean = false): TextView = TextView(ctx).apply {
        textSize = size
        setTextColor(ContextCompat.getColor(ctx, R.color.ink))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    fun gray(ctx: Context, size: Float = 12f): TextView = TextView(ctx).apply {
        textSize = size
        setTextColor(ContextCompat.getColor(ctx, R.color.ink2))
    }

    fun kindName(k: MatchKind): String = when (k) {
        MatchKind.TEXT_EQ -> "文本等于"
        MatchKind.TEXT_CONTAINS -> "文本包含"
        MatchKind.TEXT_REGEX -> "文本正则"
        MatchKind.ID_REGEX -> "ID 正则"
        MatchKind.DESC_CONTAINS -> "描述包含"
    }

    fun actionName(a: RuleAction): String = when (a) {
        RuleAction.CLICK_SELF -> "点它自己"
        RuleAction.CLICK_PARENT -> "点可点击父级"
        RuleAction.TAP_CENTER -> "点中心坐标"
        RuleAction.BACK -> "按返回键"
    }

    fun allKinds(): Array<String> = MatchKind.values().map { kindName(it) }.toTypedArray()
    fun allActions(): Array<String> = RuleAction.values().map { actionName(it) }.toTypedArray()
}
