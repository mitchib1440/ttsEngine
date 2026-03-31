package com.k2fsa.sherpa.onnx.tts.engine

object TextNormalizer {

    data class PronunciationRule(
        val pattern: Regex,
        val replacement: String,
    )

    private val ruleLock = Any()

    // Keep defaults aligned with repository seed rules.
    private val pronunciationRules = mutableListOf(
        PronunciationRule(Regex("(?i)\\bTTS\\b"), "tee tee ess"),
        PronunciationRule(Regex("(?i)\\bLLM\\b"), "ell ell em"),
        PronunciationRule(Regex("(?i)\\bSpeakThat\\b"), "speak that"),
    )

    private val symbolReplacements = listOf(
        Regex("…") to ".",
        Regex("[“”]") to "\"",
        Regex("[‘’]") to "'",
        Regex("[•·]") to ", ",
    )

    fun normalize(input: String): String {
        var normalized = input
        normalized = applyPronunciationRules(normalized)
        normalized = sanitizePunctuation(normalized)
        return normalizeWhitespace(normalized)
    }

    fun sanitizePunctuation(text: String): String {
        var sanitized = text

        for ((pattern, replacement) in symbolReplacements) {
            sanitized = pattern.replace(sanitized, replacement)
        }

        // Convert spoken-pause dashes into commas for better mid-sentence prosody.
        sanitized = sanitized.replace(Regex("\\s+-\\s+"), ", ")
        sanitized = sanitized.replace(Regex("\\s+[–—]\\s+"), ", ")
        sanitized = sanitized.replace(Regex("([\\p{L}\\d])[–—]([\\p{L}\\d])"), "$1, $2")
        // Keep dramatic pause intent from ellipses.
        sanitized = sanitized.replace(Regex("\\.{2,}"), ". ")

        sanitized = sanitized.replace(Regex("\\s+([,.!?;:])"), "$1")
        sanitized = sanitized.replace(Regex("([.!?;:])(?!\\s|$)"), "$1 ")
        sanitized = sanitized.replace(Regex("([,!?;:])\\1+"), "$1")

        return sanitized
    }

    fun applyPronunciationRules(text: String): String {
        val rulesSnapshot = synchronized(ruleLock) { pronunciationRules.toList() }
        var transformed = text
        for (rule in rulesSnapshot) {
            transformed = rule.pattern.replace(transformed, rule.replacement)
        }
        return transformed
    }

    fun addRule(pattern: Regex, replacement: String) {
        synchronized(ruleLock) {
            pronunciationRules.add(PronunciationRule(pattern, replacement))
        }
    }

    fun removeRule(pattern: Regex): Boolean {
        synchronized(ruleLock) {
            return pronunciationRules.removeAll { it.pattern.pattern == pattern.pattern }
        }
    }

    fun replaceRules(rules: List<PronunciationRule>) {
        synchronized(ruleLock) {
            pronunciationRules.clear()
            pronunciationRules.addAll(rules)
        }
    }

    fun getRules(): List<PronunciationRule> {
        return synchronized(ruleLock) { pronunciationRules.toList() }
    }

    private fun normalizeWhitespace(text: String): String {
        return text.replace(Regex("\\s+"), " ").trim()
    }
}
