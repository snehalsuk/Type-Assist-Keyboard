package com.aikeyboard.assistant.ime

/** AI actions offered from the keyboard's toolbar, and the prompts that drive them. */
sealed class AiAction(val label: String) {
    object FixGrammar : AiAction("Fix Grammar")
    data class Tone(val style: String) : AiAction(style)
    data class Translate(val language: String) : AiAction(language)
    object Summarize : AiAction("Summarize")
    object Humanize : AiAction("Humanize")
    object Emojify : AiAction("Emojify")
    object Caption : AiAction("Caption")
    object SmartReply : AiAction("Reply")
    data class CustomPrompt(val instruction: String) : AiAction("Custom")

    val systemPrompt: String
        get() = when (this) {
            is FixGrammar ->
                "You are a grammar and spelling correction tool. Rewrite the user's text " +
                    "fixing grammar, spelling, and punctuation errors. Preserve their meaning, " +
                    "tone, and formatting. Reply with ONLY the corrected text -- no quotes, " +
                    "no explanations."
            is Tone ->
                "Rewrite the user's text in a $style tone. Keep the same meaning and " +
                    "approximately the same length. Reply with ONLY the rewritten text -- no " +
                    "quotes, no explanations."
            is Translate ->
                "Translate the user's text into $language, preserving its tone, meaning, and " +
                    "formatting as closely as possible. Reply with ONLY the translated text " +
                    "-- no quotes, no explanations."
            is Summarize ->
                "Summarize the user's text concisely in 1-3 sentences, preserving the key " +
                    "meaning. Reply with ONLY the summary -- no quotes, no explanations."
            is Humanize ->
                "Rewrite the user's text so it reads as natural, human-written prose: vary " +
                    "sentence rhythm and remove robotic or overly formal phrasing. Keep the " +
                    "same meaning and approximate length. Reply with ONLY the rewritten text " +
                    "-- no quotes, no explanations."
            is Emojify ->
                "Rewrite the user's text adding relevant emojis throughout to make it more " +
                    "expressive, without changing its wording or meaning. Reply with ONLY the " +
                    "result -- no quotes, no explanations."
            is Caption ->
                "Turn the user's text into a short, catchy social media caption, adding " +
                    "relevant hashtags if fitting. Reply with ONLY the caption -- no quotes, " +
                    "no explanations."
            is SmartReply ->
                "The user received the message below and wants to reply to it. Write exactly " +
                    "three short, natural replies (1-2 sentences each), in this exact order: " +
                    "a positive/agreeable reply, a neutral reply, and a negative/declining " +
                    "reply. Reply with ONLY the three replies, one per line, in that order -- " +
                    "no numbering, no labels, no quotes, no explanations."
            is CustomPrompt ->
                "Follow this instruction exactly when rewriting the user's text: " +
                    "\"$instruction\". Reply with ONLY the result -- no quotes, no explanations."
        }
}

/** Tone rewrite styles shown when the user taps "Tone" -- matches CleverType's preset list. */
val ToneStyles = listOf(
    "Professional", "Casual", "Polite", "Romantic", "Empathetic", "Funny",
    "Poetic", "Shorten", "Sarcastic", "Angry", "Flirty", "Gen-Z", "Witty",
)

/** Languages shown when the user taps "Translate". */
val TranslateLanguages = listOf(
    "Spanish", "French", "German", "Hindi", "Japanese", "Chinese",
    "Portuguese", "Arabic", "Russian", "Italian", "Korean", "Dutch",
)

/** Splits SmartReply's three-line response into labeled (sentiment, text) options. */
fun parseReplyVariants(raw: String): List<Pair<String, String>> {
    val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
    if (lines.size < 3) return emptyList()
    return listOf("Positive", "Neutral", "Negative").zip(lines.take(3))
}
