package com.jingchuang.sweep.core

import org.json.JSONObject

enum class MatchKind { TEXT_EQ, TEXT_CONTAINS, TEXT_REGEX, ID_REGEX, DESC_CONTAINS }

enum class RuleAction { CLICK_SELF, CLICK_PARENT, TAP_CENTER, BACK }

/**
 * 一条规则 = 在哪（pkg）+ 认什么（kind/value）+ 怎么做（action）。
 * pkg 为空串表示全局规则，对所有启用的应用生效。
 */
data class Rule(
    val id: String,
    val pkg: String = "",
    val kind: MatchKind = MatchKind.TEXT_CONTAINS,
    val value: String = "",
    val action: RuleAction = RuleAction.CLICK_PARENT,
    val enabled: Boolean = true,
    val note: String = "",
    val hit: Int = 0
) {
    val isGlobal: Boolean get() = pkg.isEmpty()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("pkg", pkg)
        put("kind", kind.name)
        put("value", value)
        put("action", action.name)
        put("enabled", enabled)
        put("note", note)
        put("hit", hit)
    }

    companion object {
        fun from(j: JSONObject) = Rule(
            id = j.optString("id", "r" + System.nanoTime()),
            pkg = j.optString("pkg", ""),
            kind = runCatching { MatchKind.valueOf(j.optString("kind", "TEXT_CONTAINS")) }
                .getOrDefault(MatchKind.TEXT_CONTAINS),
            value = j.optString("value", ""),
            action = runCatching { RuleAction.valueOf(j.optString("action", "CLICK_PARENT")) }
                .getOrDefault(RuleAction.CLICK_PARENT),
            enabled = j.optBoolean("enabled", true),
            note = j.optString("note", ""),
            hit = j.optInt("hit", 0)
        )

        fun make(
            pkg: String,
            kind: MatchKind,
            value: String,
            action: RuleAction = RuleAction.CLICK_PARENT,
            note: String = ""
        ) = Rule(
            id = "r" + System.currentTimeMillis() + (100..999).random(),
            pkg = pkg,
            kind = kind,
            value = value,
            action = action,
            note = note
        )
    }
}
