package com.jingchuang.sweep.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 规则的读写与默认种子。规则量很小（几十条），整份 JSON 存 SharedPreferences 足够。
 */
object RuleStore {

    private const val FILE = "rules"
    private const val KEY_LIST = "list"
    private const val KEY_SEED = "seed_v"
    private const val SEED_VERSION = 3

    private fun sp(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(ctx: Context): MutableList<Rule> {
        ensureSeeded(ctx)
        val raw = sp(ctx).getString(KEY_LIST, null) ?: return mutableListOf()
        return runCatching {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { i -> Rule.from(arr.getJSONObject(i)) }
        }.getOrDefault(mutableListOf())
    }

    fun save(ctx: Context, list: List<Rule>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        sp(ctx).edit().putString(KEY_LIST, arr.toString()).apply()
    }

    fun add(ctx: Context, rule: Rule) {
        val list = load(ctx)
        // 同 pkg + kind + value 视为重复，直接替换
        list.removeAll { it.pkg == rule.pkg && it.kind == rule.kind && it.value == rule.value }
        list.add(0, rule)
        save(ctx, list)
    }

    fun delete(ctx: Context, id: String) {
        val list = load(ctx)
        list.removeAll { it.id == id }
        save(ctx, list)
    }

    fun setEnabled(ctx: Context, id: String, on: Boolean) {
        val list = load(ctx)
        val i = list.indexOfFirst { it.id == id }
        if (i >= 0) {
            list[i] = list[i].copy(enabled = on)
            save(ctx, list)
        }
    }

    fun bump(ctx: Context, id: String) {
        val list = load(ctx)
        val i = list.indexOfFirst { it.id == id }
        if (i >= 0) {
            list[i] = list[i].copy(hit = list[i].hit + 1)
            save(ctx, list)
        }
    }

    fun ensureSeeded(ctx: Context) {
        if (sp(ctx).getInt(KEY_SEED, 0) >= SEED_VERSION) return
        val existing = load0(ctx)
        val have = existing.map { it.pkg + "|" + it.kind + "|" + it.value }.toHashSet()
        val merged = ArrayList<Rule>(existing)
        defaults().forEach { if (have.add(it.pkg + "|" + it.kind + "|" + it.value)) merged.add(it) }
        save(ctx, merged)
        sp(ctx).edit().putInt(KEY_SEED, SEED_VERSION).apply()
    }

    private fun load0(ctx: Context): MutableList<Rule> {
        val raw = sp(ctx).getString(KEY_LIST, null) ?: return mutableListOf()
        return runCatching {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { i -> Rule.from(arr.getJSONObject(i)) }
        }.getOrDefault(mutableListOf())
    }

    /** 内置全局规则：覆盖国内主流广告 SDK 的关闭/跳过文案与常见 id */
    fun defaults(): List<Rule> = listOf(
        g("t1", MatchKind.TEXT_CONTAINS, "跳过广告"),
        g("t2", MatchKind.TEXT_CONTAINS, "跳过"),
        g("t3", MatchKind.TEXT_CONTAINS, "关闭广告"),
        g("t4", MatchKind.TEXT_CONTAINS, "关闭"),
        g("t5", MatchKind.TEXT_EQ, "×"),
        g("t6", MatchKind.TEXT_EQ, "✕"),
        g("t7", MatchKind.TEXT_EQ, "X"),
        g("t8", MatchKind.DESC_CONTAINS, "关闭"),
        g("t9", MatchKind.TEXT_CONTAINS, "我知道了"),
        g("t10", MatchKind.TEXT_CONTAINS, "以后再说"),
        g("t11", MatchKind.TEXT_CONTAINS, "残忍"),
        g("t12", MatchKind.TEXT_CONTAINS, "暂不"),
        g("t13", MatchKind.TEXT_CONTAINS, "稍后再说"),
        g("t14", MatchKind.TEXT_CONTAINS, "下次再说"),
        g("t15", MatchKind.TEXT_CONTAINS, "以后再说吧"),
        g("i1", MatchKind.ID_REGEX, "(?i).*(close|skip|dislike|adclose).*")
    )

    private fun g(id: String, kind: MatchKind, value: String) =
        Rule(
            id = id,
            pkg = "",
            kind = kind,
            value = value,
            action = RuleAction.CLICK_PARENT,
            enabled = true,
            note = "内置"
        )

    fun toJson(list: List<Rule>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        return arr.toString()
    }

    fun fromJson(raw: String): List<Rule> = runCatching {
        val arr = JSONArray(raw)
        MutableList(arr.length()) { i -> Rule.from(arr.getJSONObject(i)) }
    }.getOrDefault(emptyList())

    fun exportRule(r: Rule): String = r.toJson().toString(2)

    fun blank(): JSONObject = JSONObject()
}
