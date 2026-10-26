package com.jingchuang.sweep.util

import android.content.Context

/**
 * 极简配置存储。全部走 SharedPreferences，不引入数据库。
 */
object Prefs {
    private const val FILE = "adsweep"

    private fun sp(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---- 总开关 ----
    fun enabled(ctx: Context): Boolean = sp(ctx).getBoolean("enabled", true)
    fun setEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("enabled", v).apply()

    // ---- 分应用开关（默认开）----
    fun pkgEnabled(ctx: Context, pkg: String): Boolean = sp(ctx).getBoolean("pkg_$pkg", true)
    fun setPkgEnabled(ctx: Context, pkg: String, v: Boolean) =
        sp(ctx).edit().putBoolean("pkg_$pkg", v).apply()

    // ---- 统计 ----
    fun totalClicks(ctx: Context): Int = sp(ctx).getInt("clicks", 0)
    fun addClicks(ctx: Context, n: Int = 1) =
        sp(ctx).edit().putInt("clicks", totalClicks(ctx) + n).apply()
    fun resetClicks(ctx: Context) = sp(ctx).edit().putInt("clicks", 0).apply()

    // ---- 规则种子版本 ----
    fun seedVersion(ctx: Context): Int = sp(ctx).getInt("seed_v", 0)
    fun setSeedVersion(ctx: Context, v: Int) = sp(ctx).edit().putInt("seed_v", v).apply()
}
