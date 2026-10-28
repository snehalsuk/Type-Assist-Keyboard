package com.aikeyboard.assistant.ime

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * Tracks recently-copied plain text, most-recent-first, for the keyboard's clipboard panel.
 * One file per entry (like StickerStore) rather than a single delimited file, so arbitrary
 * clipboard content -- including newlines -- never needs escaping.
 */
class ClipboardHistoryStore(context: Context) {
    private val dir = File(context.filesDir, "clipboard_history").apply { mkdirs() }

    fun list(): List<String> =
        dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.mapNotNull { runCatching { it.readText() }.getOrNull() }
            .orEmpty()

    fun record(text: String) {
        if (text.isBlank()) return
        if (list().firstOrNull() == text) return
        runCatching {
            File(dir, "${System.currentTimeMillis()}-${UUID.randomUUID()}.txt").writeText(text)
        }
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        files.drop(MAX_ENTRIES).forEach { it.delete() }
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private companion object {
        const val MAX_ENTRIES = 30
    }
}
