package com.aikeyboard.assistant.ime

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** A named color set the user can pick for the keyboard's chrome and keys. */
data class KeyboardTheme(
    val id: String,
    val label: String,
    val background: Color,
    val key: Color,
    val accent: Color,
    val text: Color,
)

val KeyboardThemes: List<KeyboardTheme> = listOf(
    KeyboardTheme("dark", "Dark", Color(0xFF1E1E1E), Color(0xFF3A3A3A), Color(0xFF4C8BF5), Color.White),
    KeyboardTheme("light", "Light", Color(0xFFECECEC), Color(0xFFFFFFFF), Color(0xFF1A73E8), Color(0xFF1A1A1A)),
    KeyboardTheme("midnight", "Midnight", Color(0xFF0D1B2A), Color(0xFF1B263B), Color(0xFF00B4D8), Color.White),
    KeyboardTheme("forest", "Forest", Color(0xFF1B2A1E), Color(0xFF2E4030), Color(0xFF6FCF97), Color.White),
    KeyboardTheme("sunset", "Sunset", Color(0xFF2A1B24), Color(0xFF3F2A38), Color(0xFFFF7A59), Color.White),
    KeyboardTheme("mono", "Mono", Color(0xFF141414), Color(0xFF262626), Color(0xFFBBBBBB), Color.White),
)

enum class KeyboardFont(val label: String, val family: FontFamily) {
    Default("Default", FontFamily.Default),
    Serif("Serif", FontFamily.Serif),
    Monospace("Monospace", FontFamily.Monospace),
    SansSerif("Sans", FontFamily.SansSerif),
}

enum class KeyboardFontSize(val label: String, val scale: Float) {
    Small("Small", 0.85f),
    Medium("Medium", 1.0f),
    Large("Large", 1.2f),
}

/** What the keyboard actually renders with -- colors, font, text scale, and background bundled together. */
data class KeyboardVisualTheme(
    val colors: KeyboardTheme,
    val font: KeyboardFont,
    val fontScale: Float,
    val backgroundImagePath: String? = null,
)

/** Persists the user's chosen theme/font/size, read by both the keyboard and the settings screen. */
class ThemeStore(context: Context) {
    private val prefs = context.getSharedPreferences("keyboard_theme", Context.MODE_PRIVATE)

    fun currentVisualTheme(): KeyboardVisualTheme =
        KeyboardVisualTheme(
            colors = currentTheme(),
            font = currentFont(),
            fontScale = currentFontSize().scale,
            backgroundImagePath = currentBackgroundImagePath(),
        )

    fun currentTheme(): KeyboardTheme {
        val id = prefs.getString(KEY_THEME, KeyboardThemes.first().id)
        return KeyboardThemes.firstOrNull { it.id == id } ?: KeyboardThemes.first()
    }

    fun setTheme(theme: KeyboardTheme) {
        prefs.edit().putString(KEY_THEME, theme.id).apply()
    }

    fun currentFont(): KeyboardFont {
        val name = prefs.getString(KEY_FONT, KeyboardFont.Default.name)
        return runCatching {
            KeyboardFont.valueOf(name ?: KeyboardFont.Default.name)
        }.getOrDefault(KeyboardFont.Default)
    }

    fun setFont(font: KeyboardFont) {
        prefs.edit().putString(KEY_FONT, font.name).apply()
    }

    fun currentFontSize(): KeyboardFontSize {
        val name = prefs.getString(KEY_FONT_SIZE, KeyboardFontSize.Medium.name)
        return runCatching {
            KeyboardFontSize.valueOf(name ?: KeyboardFontSize.Medium.name)
        }.getOrDefault(KeyboardFontSize.Medium)
    }

    fun setFontSize(size: KeyboardFontSize) {
        prefs.edit().putString(KEY_FONT_SIZE, size.name).apply()
    }

    fun currentBackgroundImagePath(): String? = prefs.getString(KEY_BG_IMAGE, null)

    fun setBackgroundImagePath(path: String?) {
        prefs.edit().putString(KEY_BG_IMAGE, path).apply()
    }

    private companion object {
        const val KEY_THEME = "theme_id"
        const val KEY_FONT = "font_name"
        const val KEY_FONT_SIZE = "font_size_name"
        const val KEY_BG_IMAGE = "background_image_path"
    }
}
