package com.aikeyboard.assistant.ime

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Learns word frequencies from what the user commits (space/enter/punctuation) and offers
 * prefix-based next-word suggestions. Purely on-device; never sees password fields (the caller
 * is responsible for not calling [learn] there).
 */
class WordSuggester(context: Context) {
    private val storeFile = File(context.filesDir, "word_frequencies.tsv")
    // learn() now runs on a background dispatcher while suggestionsFor() is still read from the
    // main thread on every keystroke -- a plain HashMap isn't safe under that concurrent access.
    private val frequencies = ConcurrentHashMap<String, Int>()

    init {
        runCatching {
            if (storeFile.exists()) {
                storeFile.forEachLine { line ->
                    val parts = line.split('\t')
                    val count = parts.getOrNull(1)?.toIntOrNull()
                    if (parts.size == 2 && count != null) {
                        frequencies[parts[0]] = count
                    }
                }
            }
        }
    }

    fun suggestionsFor(prefix: String, limit: Int = 3): List<String> {
        val lower = prefix.lowercase()
        if (lower.isBlank()) return emptyList()
        return frequencies.entries
            .asSequence()
            .filter { it.key.startsWith(lower) && it.key != lower }
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
            .toList()
    }

    fun learn(word: String) {
        val cleaned = word.lowercase().filter { it.isLetter() }
        if (cleaned.length < 2) return
        frequencies.merge(cleaned, 1, Int::plus)
        persist()
    }

    private fun persist() {
        runCatching {
            storeFile.bufferedWriter().use { writer ->
                frequencies.entries
                    .sortedByDescending { it.value }
                    .take(2000)
                    .forEach { (word, count) ->
                        writer.write("$word\t$count")
                        writer.newLine()
                    }
            }
        }
    }
}
