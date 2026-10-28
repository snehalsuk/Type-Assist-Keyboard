package com.aikeyboard.assistant.ime

import android.content.Context
import java.io.File

/** Tracks recently-used emoji, most-recent-first, for the panel's "Recent" tab. */
class RecentEmojiStore(context: Context) {
    private val file = File(context.filesDir, "recent_emoji.txt")
    private val recent = mutableListOf<String>()

    init {
        runCatching {
            if (file.exists()) recent.addAll(file.readLines().filter { it.isNotBlank() })
        }
    }

    fun list(): List<String> = recent.toList()

    fun record(emoji: String) {
        recent.remove(emoji)
        recent.add(0, emoji)
        while (recent.size > MAX_RECENT) recent.removeAt(recent.lastIndex)
        runCatching { file.writeText(recent.joinToString("\n")) }
    }

    private companion object {
        const val MAX_RECENT = 30
    }
}
