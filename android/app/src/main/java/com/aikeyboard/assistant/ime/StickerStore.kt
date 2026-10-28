package com.aikeyboard.assistant.ime

import android.content.Context
import java.io.File
import java.util.UUID

/** Manages sticker/image files saved locally (e.g. pasted from the clipboard) for reuse. */
class StickerStore(context: Context) {
    private val dir = File(context.filesDir, "stickers").apply { mkdirs() }

    fun saveFromBytes(bytes: ByteArray, mimeType: String): File {
        val extension = when {
            mimeType.contains("png") -> "png"
            mimeType.contains("gif") -> "gif"
            mimeType.contains("webp") -> "webp"
            else -> "jpg"
        }
        val target = File(dir, "${UUID.randomUUID()}.$extension")
        target.writeBytes(bytes)
        return target
    }

    fun list(): List<File> = dir.listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()

    fun delete(file: File) {
        file.delete()
    }
}
