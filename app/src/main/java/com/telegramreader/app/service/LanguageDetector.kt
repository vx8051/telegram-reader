package com.telegramreader.app.service

import android.content.Context
import android.os.Build
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextLanguage
import java.util.Locale

/**
 * Picks the voice locale for a piece of text from the user's configured list.
 * Uses Android's on-device TextClassifier (API 29+) and falls back to a script heuristic.
 */
class LanguageDetector(context: Context) {
    private val appContext = context.applicationContext

    /**
     * @param candidates locales the user wants to hear, in preference order. Empty = device default.
     * @return the candidate whose language best matches [text], else the first candidate.
     */
    fun choose(text: String, candidates: List<Locale>): Locale? {
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates[0]
        val detected = detect(text)
        for (d in detected) {
            candidates.firstOrNull { it.language.equals(d.language, ignoreCase = true) }?.let { return it }
        }
        return candidates[0]
    }

    /** Ranked language hypotheses for [text]. Blocking; call off the main thread. */
    fun detect(text: String): List<Locale> {
        val sample = text.take(1000)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val tc = appContext.getSystemService(TextClassificationManager::class.java).textClassifier
                val result = tc.detectLanguage(TextLanguage.Request.Builder(sample).build())
                val locales = (0 until result.localeHypothesisCount).map { result.getLocale(it) }
                    .filter { result.getConfidenceScore(it) >= 0.3f }
                    .map { it.toLocale() }
                if (locales.isNotEmpty()) return locales
            } catch (e: Exception) {
                // fall through to heuristic
            }
        }
        return listOfNotNull(scriptGuess(sample))
    }

    /** Cheap guess from the dominant script; distinguishes Ukrainian from Russian by letters unique to each. */
    private fun scriptGuess(text: String): Locale? {
        var cyrillic = 0; var latin = 0; var uk = 0; var ru = 0
        var greek = 0; var hebrew = 0; var arabic = 0; var cjk = 0
        for (ch in text) {
            when (Character.UnicodeBlock.of(ch)) {
                Character.UnicodeBlock.CYRILLIC -> {
                    cyrillic++
                    when (ch.lowercaseChar()) { 'і', 'ї', 'є', 'ґ' -> uk++; 'ы', 'э', 'ъ', 'ё' -> ru++ }
                }
                Character.UnicodeBlock.BASIC_LATIN, Character.UnicodeBlock.LATIN_1_SUPPLEMENT,
                Character.UnicodeBlock.LATIN_EXTENDED_A -> if (ch.isLetter()) latin++
                Character.UnicodeBlock.GREEK -> greek++
                Character.UnicodeBlock.HEBREW -> hebrew++
                Character.UnicodeBlock.ARABIC -> arabic++
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS, Character.UnicodeBlock.HIRAGANA,
                Character.UnicodeBlock.KATAKANA, Character.UnicodeBlock.HANGUL_SYLLABLES -> cjk++
                else -> {}
            }
        }
        val max = maxOf(cyrillic, latin, greek, hebrew, arabic, cjk)
        return when {
            max == 0 -> null
            max == cyrillic -> Locale(if (uk >= ru) "uk" else "ru")
            max == latin -> Locale("en")
            max == greek -> Locale("el")
            max == hebrew -> Locale("he")
            max == arabic -> Locale("ar")
            else -> Locale("zh")
        }
    }
}
