package com.aikeyboard.assistant.ime

import java.io.File

/** Everything KeyboardScreen needs from the service, bundled so the composable signature stays sane. */
class KeyboardCallbacks(
    val onKeyAction: (KeyAction) -> Unit,
    val onGetFieldText: () -> String,
    val onReplaceFieldText: (String) -> Unit,
    val onRunAiAction: suspend (AiAction, String) -> Result<String>,
    val onSwitchKeyboard: () -> Unit,
    val onGetSuggestions: () -> List<String>,
    val onPickSuggestion: (String) -> Unit,
    val onStartSpeech: suspend () -> Result<String>,
    val onGetRecentEmoji: () -> List<String>,
    val onRecordEmojiUse: (String) -> Unit,
    val onGetStickers: () -> List<File>,
    val onSaveStickerFromClipboard: () -> Boolean,
    val onInsertSticker: suspend (File) -> Result<Unit>,
    val onGetTrendingGifs: suspend () -> Result<List<GifResult>>,
    val onInsertGif: suspend (String) -> Result<Unit>,
    val onGetClipboardHistory: () -> List<String>,
    val onClearClipboardHistory: () -> Unit,
    val onOpenSettings: () -> Unit,
    /** Whether the next letter should be capitalized (sentence start, per the field's own cap flags). */
    val onShouldAutoCapitalize: () -> Boolean,
)
