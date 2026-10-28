package com.aikeyboard.assistant.ime

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local, encrypted, per-day log of committed text. Never leaves the device and is never sent
 * anywhere -- the caller (KeyboardService) is responsible for never calling [append] for
 * password fields.
 */
class TypingHistoryStore(private val context: Context) {
    private val dir = File(context.filesDir, "typing_history").apply { mkdirs() }

    private val masterKey by lazy {
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    }

    fun append(text: String) {
        if (text.isBlank()) return
        val file = File(dir, "${today()}.enc")
        writeDay(file, readDay(file) + text)
    }

    fun entriesFor(day: String): String = readDay(File(dir, "$day.enc"))

    fun availableDays(): List<String> =
        dir.listFiles { f -> f.name.endsWith(".enc") }
            ?.map { it.name.removeSuffix(".enc") }
            ?.sortedDescending()
            .orEmpty()

    fun clearAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun readDay(file: File): String {
        if (!file.exists()) return ""
        return runCatching {
            encryptedFile(file).openFileInput().use { it.readBytes().decodeToString() }
        }.getOrDefault("")
    }

    private fun writeDay(file: File, content: String) {
        if (file.exists()) file.delete()
        runCatching {
            encryptedFile(file).openFileOutput().use { it.write(content.toByteArray()) }
        }
    }

    private fun encryptedFile(file: File): EncryptedFile =
        EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
