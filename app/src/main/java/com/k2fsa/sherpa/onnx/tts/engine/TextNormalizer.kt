package com.k2fsa.sherpa.onnx.tts.engine

object TextNormalizer {
    private val unsupportedLanguageBlockRegex =
        Regex("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}]+")

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
        normalized = filterUnsupportedLanguages(normalized)
        normalized = applyPronunciationRules(normalized)
        normalized = sanitizePunctuation(normalized)
        return normalizeWhitespace(normalized)
    }

    fun filterUnsupportedLanguages(text: String): String {
        return unsupportedLanguageBlockRegex.replace(text, " [Foreign text] ")
    }

    fun sanitizePunctuation(text: String): String {
        var sanitized = text

        for ((pattern, replacement) in symbolReplacements) {
            sanitized = pattern.replace(sanitized, replacement)
        }

        // Force pauses for line breaks: add a sentence stop when previous char is not punctuation.
        sanitized = sanitized.replace(Regex("(?<![.!?,:;])\\s*[\\r\\n]+\\s*"), ". ")
        // Remaining line breaks already follow punctuation, so collapse to a single space.
        sanitized = sanitized.replace(Regex("\\s*[\\r\\n]+\\s*"), " ")

        // Convert spoken-pause dashes into commas for better mid-sentence prosody.
        sanitized = sanitized.replace(Regex("\\s+-\\s+"), ", ")
        sanitized = sanitized.replace(Regex("\\s+[–—]\\s+"), ", ")
        sanitized = sanitized.replace(Regex("([\\p{L}\\d])[–—]([\\p{L}\\d])"), "$1, $2")
        // Keep dramatic pause intent from ellipses.
        sanitized = sanitized.replace(Regex("\\.{2,}"), ". ")
        // Drop redundant colons after terminal punctuation (e.g., ".:").
        sanitized = sanitized.replace(Regex("([.!?])\\s*:"), "$1 ")
        // Convert non-time colons into commas for a natural pause.
        sanitized = sanitized.replace(Regex("(?<!\\d):(?!\\d)"), ", ")

        sanitized = sanitized.replace(Regex("\\s+([,.!?;:])"), "$1")
        sanitized = sanitized.replace(Regex("([.!?;])(?!\\s|$)"), "$1 ")
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
