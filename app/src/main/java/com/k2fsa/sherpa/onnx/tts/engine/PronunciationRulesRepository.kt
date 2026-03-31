package com.k2fsa.sherpa.onnx.tts.engine

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class PronunciationRulesRepository(context: Context) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PreferenceHelper.PREFS_NAME, Context.MODE_PRIVATE)

    fun loadRules(): List<TextNormalizer.PronunciationRule> {
        if (!sharedPreferences.contains(RULES_KEY)) {
            saveRules(DEFAULT_RULES)
            return DEFAULT_RULES
        }

        val json = sharedPreferences.getString(RULES_KEY, "[]") ?: "[]"
        return parseRules(json)
    }

    fun saveRules(rules: List<TextNormalizer.PronunciationRule>): Boolean {
        val validRules = mutableListOf<TextNormalizer.PronunciationRule>()
        for (rule in rules) {
            val validation = validateRule(rule.pattern.pattern, rule.replacement)
            if (validation.isFailure) {
                return false
            }
            validRules.add(rule)
        }

        if (validRules.size > MAX_RULES) {
            return false
        }

        val serialized = serializeRules(validRules)
        return sharedPreferences.edit().putString(RULES_KEY, serialized).commit()
    }

    fun addRule(pattern: String, replacement: String): Result<Unit> {
        val validation = validateRule(pattern, replacement)
        if (validation.isFailure) {
            return validation
        }

        val current = loadRules().toMutableList()
        if (current.size >= MAX_RULES) {
            return Result.failure(IllegalArgumentException("Maximum of $MAX_RULES rules reached"))
        }

        current.add(TextNormalizer.PronunciationRule(Regex(pattern.trim()), replacement))
        return if (saveRules(current)) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Failed to save pronunciation rules"))
        }
    }

    fun deleteRuleAt(index: Int): Result<Unit> {
        val current = loadRules().toMutableList()
        if (index !in current.indices) {
            return Result.failure(IndexOutOfBoundsException("Rule index out of range"))
        }
        current.removeAt(index)
        return if (saveRules(current)) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Failed to save pronunciation rules"))
        }
    }

    private fun validateRule(pattern: String, replacement: String): Result<Unit> {
        val normalizedPattern = pattern.trim()

        if (normalizedPattern.isEmpty() || replacement.trim().isEmpty()) {
            return Result.failure(IllegalArgumentException("Pattern and pronunciation are required"))
        }

        if (normalizedPattern.length > MAX_PATTERN_LENGTH || replacement.length > MAX_REPLACEMENT_LENGTH) {
            return Result.failure(IllegalArgumentException("Pattern or pronunciation is too long"))
        }

        return try {
            Regex(normalizedPattern)
            Result.success(Unit)
        } catch (exception: Exception) {
            Result.failure(IllegalArgumentException("Invalid regex pattern"))
        }
    }

    private fun serializeRules(rules: List<TextNormalizer.PronunciationRule>): String {
        val array = JSONArray()
        for (rule in rules) {
            val obj = JSONObject()
            obj.put("pattern", rule.pattern.pattern)
            obj.put("replacement", rule.replacement)
            array.put(obj)
        }
        return array.toString()
    }

    private fun parseRules(json: String): List<TextNormalizer.PronunciationRule> {
        return try {
            val parsed = mutableListOf<TextNormalizer.PronunciationRule>()
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val pattern = item.optString("pattern", "").trim()
                val replacement = item.optString("replacement", "")
                val validation = validateRule(pattern, replacement)
                if (validation.isSuccess) {
                    parsed.add(TextNormalizer.PronunciationRule(Regex(pattern), replacement))
                }
            }
            parsed
        } catch (exception: Exception) {
            DEFAULT_RULES
        }
    }

    companion object {
        const val ACTION_RULES_UPDATED = "com.k2fsa.sherpa.onnx.ACTION_RULES_UPDATED"
        private const val RULES_KEY = "pronunciation_rules_json"
        private const val MAX_RULES = 100
        private const val MAX_PATTERN_LENGTH = 128
        private const val MAX_REPLACEMENT_LENGTH = 128

        val DEFAULT_RULES = listOf(
            TextNormalizer.PronunciationRule(Regex("(?i)\\bTTS\\b"), "tee tee ess"),
            TextNormalizer.PronunciationRule(Regex("(?i)\\bLLM\\b"), "ell ell em"),
            TextNormalizer.PronunciationRule(Regex("(?i)\\bSpeakThat\\b"), "speak that"),
        )
    }
}
