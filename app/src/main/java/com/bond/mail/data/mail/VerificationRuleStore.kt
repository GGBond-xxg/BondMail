package com.bond.mail.data.mail

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class VerificationRuleStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("verification_rules", Context.MODE_PRIVATE)

    fun rules(): List<VerificationRule> = runCatching {
        val array = JSONArray(preferences.getString("rules", "[]"))
        (0 until minOf(array.length(), MAX_RULES)).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                VerificationRule(item.getString("id"), item.getString("template"), item.getInt("length"),
                    CodeAlphabet.valueOf(item.getString("alphabet")), item.optBoolean("enabled", true))
                    .takeIf { it.isValid() }
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    val changes = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "rules") trySend(rules())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(rules())
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    suspend fun save(rules: List<VerificationRule>) = withContext(Dispatchers.IO) {
        require(rules.size <= MAX_RULES && rules.all { it.isValid() })
        val array = JSONArray()
        rules.forEach { rule -> array.put(JSONObject().put("id", rule.id).put("template", rule.template)
            .put("length", rule.length).put("alphabet", rule.alphabet.name).put("enabled", rule.enabled)) }
        check(preferences.edit().putString("rules", array.toString()).commit())
    }

    companion object { const val MAX_RULES = 20 }
}
