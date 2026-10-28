package com.aikeyboard.assistant.ime

/** One emoji category: a representative icon for its tab, plus the emoji shown when it's selected. */
data class EmojiCategory(val icon: String, val label: String, val emoji: List<String>)

/** A curated set of commonly used emoji, grouped by category for the keyboard's emoji panel. */
val EmojiCategories: List<EmojiCategory> = listOf(
    EmojiCategory(
        icon = "😀",
        label = "Smileys",
        emoji = listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃",
            "😉", "😊", "😇", "🥰", "😍", "🤩", "😘", "😗", "😚", "😙",
            "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔",
            "😐", "😑", "😶", "🙄", "😏", "😣", "😥", "😮", "🤐", "😯",
            "😪", "😫", "🥱", "😴", "😌", "😷", "🤒", "🤕", "🤢",
        ),
    ),
    EmojiCategory(
        icon = "👍",
        label = "Gestures",
        emoji = listOf(
            "👍", "👎", "👌", "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆",
            "👇", "☝️", "👋", "🤚", "🖐️", "✋", "🖖", "👏", "🙌", "🤝",
            "🙏", "✍️", "💪", "🦾", "🫡",
        ),
    ),
    EmojiCategory(
        icon = "❤️",
        label = "Hearts",
        emoji = listOf(
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
            "❤️‍🔥", "❤️‍🩹", "💕", "💞", "💓", "💗", "💖", "💘", "💝",
        ),
    ),
    EmojiCategory(
        icon = "🐶",
        label = "Animals",
        emoji = listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯",
            "🦁", "🐮", "🐷", "🐸", "🐵", "🐔", "🐧", "🐦", "🦄", "🐴",
        ),
    ),
    EmojiCategory(
        icon = "🍕",
        label = "Food",
        emoji = listOf(
            "🍎", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🍒", "🍕", "🍔",
            "🍟", "🌭", "🍿", "🧁", "🍩", "🍪", "☕", "🍵", "🍺", "🍻",
        ),
    ),
    EmojiCategory(
        icon = "⚽",
        label = "Activities",
        emoji = listOf(
            "⚽", "🏀", "🏈", "🎾", "🎮", "🎧", "🎸", "🎉", "🎂", "🎁",
            "🔥", "✨", "⭐", "🌟", "💯", "✅", "❌", "❓", "❗", "💡",
        ),
    ),
    EmojiCategory(
        icon = "✈️",
        label = "Travel",
        emoji = listOf(
            "🚗", "🚕", "🚀", "✈️", "🚲", "⛵", "🏠", "🌍", "🌈", "☀️",
            "🌙", "⏰", "📱", "💻", "📷", "🎵",
        ),
    ),
)
