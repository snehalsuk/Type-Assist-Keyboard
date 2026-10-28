package com.aikeyboard.assistant.ime

/** Static layout data for the keyboard's letter and symbol rows. */
object KeyboardLayout {
    val letterRows: List<List<Char>> = listOf(
        listOf('q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'),
        listOf('a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l'),
        listOf('z', 'x', 'c', 'v', 'b', 'n', 'm'),
    )

    val symbolRows: List<List<Char>> = listOf(
        listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '0'),
        listOf('@', '#', '$', '_', '&', '-', '+', '(', ')', '/'),
        listOf('*', '"', '\'', ':', ';', '!', '?'),
    )
}

/** Actions the keyboard's non-letter keys can trigger. */
sealed class KeyAction {
    data class Character(val char: Char) : KeyAction()
    /** For multi-codepoint inserts (emoji, ZWJ sequences) that don't fit in a single Char. */
    data class Text(val text: String) : KeyAction()
    object Shift : KeyAction()
    object Backspace : KeyAction()
    object Space : KeyAction()
    object Enter : KeyAction()
}
