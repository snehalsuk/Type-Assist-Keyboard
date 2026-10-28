package com.aikeyboard.assistant.ime

import android.Manifest
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.aikeyboard.assistant.KeyboardSettingsActivity
import com.aikeyboard.assistant.RequestMicPermissionActivity
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Compose does not get a LifecycleOwner/ViewModelStoreOwner/SavedStateRegistryOwner for free
 * outside an Activity/Fragment, so this Service supplies all three manually before handing a
 * ComposeView back from onCreateInputView().
 */
class KeyboardService :
    InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry

    override val viewModelStore = ViewModelStore()

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    // Cached across onCreateInputView() calls -- the IME reuses one view across input fields.
    private var composeView: ComposeView? = null
    private var themeState: MutableState<KeyboardVisualTheme>? = null
    private var imeActionState: MutableState<Int>? = null
    private var screenHeightDpState: MutableState<Int>? = null
    // Bumped on every onStartInputView so the keyboard re-evaluates auto-capitalization for the new field.
    private var inputSessionState: MutableState<Int>? = null

    private lateinit var wordSuggester: WordSuggester
    private lateinit var typingHistoryStore: TypingHistoryStore
    private lateinit var recentEmojiStore: RecentEmojiStore
    private lateinit var stickerStore: StickerStore
    private lateinit var themeStore: ThemeStore
    private lateinit var clipboardHistoryStore: ClipboardHistoryStore
    private val httpClient by lazy { OkHttpClient() }

    // Learning a word re-encrypts and rewrites the whole day's typing history, which gets
    // slower as the day's history grows -- limitedParallelism(1) keeps writes off the main
    // thread (so keystrokes never block on it) while still running them strictly one at a
    // time, so two quick words in a row can't race and clobber each other's write.
    private val wordLearningDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        recordClipboardChange()
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        wordSuggester = WordSuggester(this)
        typingHistoryStore = TypingHistoryStore(this)
        recentEmojiStore = RecentEmojiStore(this)
        stickerStore = StickerStore(this)
        themeStore = ThemeStore(this)
        clipboardHistoryStore = ClipboardHistoryStore(this)
        (getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager)
            ?.addPrimaryClipChangedListener(clipboardListener)
    }

    /**
     * Reads whatever text just landed on the system clipboard and records it for the keyboard's
     * clipboard panel. Wrapped defensively: Android 10+ can silently withhold clipboard content
     * from apps that aren't the focused window, and this listener can fire at any time.
     */
    private fun recordClipboardChange() {
        runCatching {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            val item = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0) ?: return
            val text = item.coerceToText(this)?.toString() ?: return
            clipboardHistoryStore.record(text)
        }
    }

    override fun onCreateInputView(): View {
        val view = composeView ?: ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycle))
            val theme = mutableStateOf(themeStore.currentVisualTheme())
            themeState = theme
            val imeAction = mutableStateOf(resolveImeAction(currentInputEditorInfo))
            imeActionState = imeAction
            val screenHeightDp = mutableStateOf(resources.configuration.screenHeightDp)
            screenHeightDpState = screenHeightDp
            val inputSession = mutableStateOf(0)
            inputSessionState = inputSession
            val callbacks = KeyboardCallbacks(
                onKeyAction = ::handleKeyAction,
                onGetFieldText = ::getFieldText,
                onReplaceFieldText = ::replaceFieldText,
                onRunAiAction = { action, text -> GroqClient.complete(action.systemPrompt, text) },
                onSwitchKeyboard = ::switchKeyboard,
                onGetSuggestions = ::getSuggestions,
                onPickSuggestion = ::applySuggestion,
                onStartSpeech = ::startSpeechRecognition,
                onGetRecentEmoji = recentEmojiStore::list,
                onRecordEmojiUse = recentEmojiStore::record,
                onGetStickers = stickerStore::list,
                onSaveStickerFromClipboard = ::saveStickerFromClipboard,
                onInsertSticker = { file -> insertContentFile(file, mimeTypeFor(file)) },
                onGetTrendingGifs = { GiphyClient.trending() },
                onInsertGif = ::downloadAndInsertGif,
                onGetClipboardHistory = clipboardHistoryStore::list,
                onClearClipboardHistory = clipboardHistoryStore::clear,
                onOpenSettings = ::openKeyboardSettings,
                onShouldAutoCapitalize = ::shouldAutoCapitalize,
            )
            setContent {
                val currentTheme by theme
                val currentImeAction by imeAction
                val currentScreenHeightDp by screenHeightDp
                val currentInputSession by inputSession
                KeyboardScreen(
                    callbacks = callbacks,
                    theme = currentTheme,
                    imeAction = currentImeAction,
                    screenHeightDp = currentScreenHeightDp,
                    inputSession = currentInputSession,
                )
            }
            setViewTreeLifecycleOwner(this@KeyboardService)
            setViewTreeViewModelStoreOwner(this@KeyboardService)
            setViewTreeSavedStateRegistryOwner(this@KeyboardService)
        }.also { composeView = it }

        // Compose resolves its window recomposer by walking up to the IME's Dialog-backed
        // window root, not just this view -- without owners there too, attaching throws
        // "ViewTreeLifecycleOwner not found" the first time the keyboard is shown.
        window?.window?.let { dialogWindow ->
            dialogWindow.decorView.setViewTreeLifecycleOwner(this)
            dialogWindow.decorView.setViewTreeViewModelStoreOwner(this)
            dialogWindow.decorView.setViewTreeSavedStateRegistryOwner(this)
            // Let Compose read navigation-bar insets itself (navigationBarsPadding()) instead of
            // the system reserving space in a way that can leave the keyboard drawn under a
            // gesture/3-button nav bar on some OEM skins.
            WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
        }
        return view
    }

    /**
     * InputMethodService's default answer here is true for landscape on phone-sized screens,
     * which hands control to the system's fullscreen "extract" edit view instead of showing this
     * Compose keyboard normally -- the visible field gets replaced by a bare system-drawn edit
     * bar and the on-screen keyboard has to fight it for space. This keyboard has its own
     * screen-height-aware sizing (see KeyboardScreen's keyHeight calculation), so it never needs
     * the system's fullscreen takeover in either orientation.
     */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        // Pick up theme/font changes made in KeyboardSettingsActivity since the keyboard was last shown.
        themeState?.value = themeStore.currentVisualTheme()
        imeActionState?.value = resolveImeAction(info)
        // Belt-and-suspenders alongside onConfigurationChanged: this also catches the case where
        // the view was rotated while hidden and onConfigurationChanged fired without the input
        // view being visible to notice.
        screenHeightDpState?.value = resources.configuration.screenHeightDp
        inputSessionState?.let { it.value += 1 }
    }

    /**
     * Compose's LocalConfiguration ambient isn't reliably kept in sync for a view manually
     * attached to the IME's own window (there's no enclosing Activity dispatching configuration
     * changes through it) -- so screen-height-driven sizing reads this explicit state instead of
     * LocalConfiguration.current, and this callback is what keeps it live across rotation while
     * the keyboard is already showing.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        screenHeightDpState?.value = newConfig.screenHeightDp
        // On-device testing found individual keys can go permanently untouchable after a
        // rotation -- Android's InputDispatcher logs the touch as delivered to this window, but
        // it never reaches Compose's pointer input for that node, while every other key on the
        // same keyboard keeps working. That smells like a stale touch-hit region left over from
        // before the window's resize rather than anything Compose-side (a full recompose of the
        // affected content made no difference). Cycling the view's visibility forces Android to
        // redo the touch-region registration for this window from scratch, the same way it does
        // on the view's first show -- which was never observed to have this problem.
        composeView?.let { view ->
            view.visibility = View.GONE
            view.post { view.visibility = View.VISIBLE }
        }
    }

    /**
     * The action a host app asked for on its Enter key (search/go/send/done/next/...), so the
     * Enter key can show a matching icon and actually trigger that action instead of just
     * inserting a newline. IME_FLAG_NO_ENTER_ACTION means the app wants a literal newline even
     * though it also declared an action (e.g. a chat app with its own separate send button).
     */
    private fun resolveImeAction(info: EditorInfo?): Int {
        val options = info?.imeOptions ?: return EditorInfo.IME_ACTION_UNSPECIFIED
        if (options and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return EditorInfo.IME_ACTION_NONE
        return options and EditorInfo.IME_MASK_ACTION
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onDestroy() {
        (getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager)
            ?.removePrimaryClipChangedListener(clipboardListener)
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    private fun handleKeyAction(action: KeyAction) {
        val ic: InputConnection = currentInputConnection ?: return
        when (action) {
            is KeyAction.Character -> ic.commitText(action.char.toString(), 1)
            is KeyAction.Text -> ic.commitText(action.text, 1)
            KeyAction.Space -> {
                recordCompletedWord()
                ic.commitText(" ", 1)
            }
            KeyAction.Enter -> {
                recordCompletedWord()
                val actionId = resolveImeAction(currentInputEditorInfo)
                if (actionId != EditorInfo.IME_ACTION_NONE && actionId != EditorInfo.IME_ACTION_UNSPECIFIED) {
                    ic.performEditorAction(actionId)
                } else {
                    ic.commitText("\n", 1)
                }
            }
            KeyAction.Backspace -> {
                ic.beginBatchEdit()
                val selected = ic.getSelectedText(0)
                if (!selected.isNullOrEmpty()) {
                    ic.commitText("", 1)
                } else {
                    // deleteSurroundingText counts UTF-16 units, not visual characters -- emoji
                    // and other surrogate-pair characters are 2 units, so deleting just 1 leaves
                    // an unpaired surrogate behind (invisible/broken glyph) and reads as "delete
                    // didn't do anything" until a second press clears the leftover half.
                    val before = ic.getTextBeforeCursor(2, 0)
                    val deleteCount = if (before != null && before.length == 2 &&
                        Character.isSurrogatePair(before[0], before[1])
                    ) 2 else 1
                    ic.deleteSurroundingText(deleteCount, 0)
                }
                ic.endBatchEdit()
            }
            KeyAction.Shift -> Unit // shift state is held locally in KeyboardScreen
        }
    }

    /** Lets the user pick any enabled IME, matching method.xml's supportsSwitchingToNextInputMethod. */
    private fun switchKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showInputMethodPicker()
    }

    /** An InputMethodService can't host its own settings UI, so it hands off to a regular Activity. */
    private fun openKeyboardSettings() {
        startActivity(
            Intent(this, KeyboardSettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Reads the full text currently in the focused field, not just what's around the cursor. */
    private fun getFieldText(): String {
        val ic = currentInputConnection ?: return ""
        return ic.getExtractedText(ExtractedTextRequest(), 0)?.text?.toString().orEmpty()
    }

    /** Replaces the full text of the focused field with AI output, regardless of cursor position. */
    private fun replaceFieldText(newText: String) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        ic.performContextMenuAction(android.R.id.selectAll)
        ic.commitText(newText, 1)
        ic.endBatchEdit()
    }

    /** The word currently being typed, i.e. everything after the last space/newline before the cursor. */
    private fun currentWordBeforeCursor(ic: InputConnection): String {
        val before = ic.getTextBeforeCursor(64, 0)?.toString().orEmpty()
        return before.substringAfterLast(' ').substringAfterLast('\n')
    }

    private fun getSuggestions(): List<String> {
        if (isPasswordField()) return emptyList()
        val ic = currentInputConnection ?: return emptyList()
        val word = currentWordBeforeCursor(ic)
        if (word.isBlank()) return emptyList()
        return wordSuggester.suggestionsFor(word)
    }

    /** Replaces the in-progress word with the chosen suggestion plus a trailing space. */
    private fun applySuggestion(word: String) {
        val ic = currentInputConnection ?: return
        val current = currentWordBeforeCursor(ic)
        ic.beginBatchEdit()
        if (current.isNotEmpty()) {
            ic.deleteSurroundingText(current.length, 0)
        }
        ic.commitText("$word ", 1)
        ic.endBatchEdit()
    }

    /**
     * Learns the just-finished word and appends it to today's on-device history -- never for
     * passwords. The InputConnection read happens here on the main thread as usual; the actual
     * disk I/O (which used to block the keystroke that triggered it) is dispatched off-thread.
     */
    private fun recordCompletedWord() {
        if (isPasswordField()) return
        val ic = currentInputConnection ?: return
        val word = currentWordBeforeCursor(ic)
        if (word.isBlank()) return
        lifecycleScope.launch(wordLearningDispatcher) {
            wordSuggester.learn(word)
            typingHistoryStore.append("$word ")
        }
    }

    /**
     * Asks the field itself whether the cursor is at a capitalization point. This honors the
     * field's TYPE_TEXT_FLAG_CAP_* flags, so it returns false in fields that opted out of
     * auto-caps (emails, URLs, usernames, passwords).
     */
    private fun shouldAutoCapitalize(): Boolean {
        val ic = currentInputConnection ?: return false
        val inputType = currentInputEditorInfo?.inputType ?: return false
        return ic.getCursorCapsMode(inputType) != 0
    }

    private fun isPasswordField(): Boolean {
        val inputType = currentInputEditorInfo?.inputType ?: return false
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputClass) {
            InputType.TYPE_CLASS_TEXT ->
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    /** Runs one speech-recognition session and returns the transcript, or a failure to show inline. */
    private suspend fun startSpeechRecognition(): Result<String> {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            startActivity(
                Intent(this, RequestMicPermissionActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return Result.failure(SecurityException("Microphone permission not granted yet"))
        }
        if (!SpeechInputHelper.isAvailable(this)) {
            return Result.failure(IOException("Speech recognition unavailable"))
        }

        var outcome: Result<String> = Result.failure(IOException("No speech result"))
        SpeechInputHelper.listen(this).collect { event ->
            outcome = when (event) {
                is SpeechEvent.Result -> Result.success(event.text)
                is SpeechEvent.Error -> Result.failure(IOException(event.message))
            }
        }
        return outcome
    }

    /** Copies an image from the clipboard (if any) into local sticker storage. Returns whether it saved one. */
    private fun saveStickerFromClipboard(): Boolean {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        val uri = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri ?: return false
        return runCatching {
            val mimeType = contentResolver.getType(uri)?.takeIf { it.startsWith("image/") }
                ?: return@runCatching false
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@runCatching false
            stickerStore.saveFromBytes(bytes, mimeType)
            true
        }.getOrDefault(false)
    }

    private fun mimeTypeFor(file: File): String = when (file.extension.lowercase()) {
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }

    /** Downloads a GIF to a local cache file, then sends it the same way a saved sticker is sent. */
    private suspend fun downloadAndInsertGif(url: String): Result<Unit> {
        val file = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).get().build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Download failed (${response.code})")
                    val bytes = response.body?.bytes() ?: throw IOException("Empty GIF response")
                    val gifCacheDir = File(cacheDir, "gif_cache").apply { mkdirs() }
                    File(gifCacheDir, "${UUID.randomUUID()}.gif").apply { writeBytes(bytes) }
                }
            }
        }.getOrElse { return Result.failure(it) }
        return insertContentFile(file, "image/gif")
    }

    /**
     * Sends a local image/GIF file straight into the focused field via the commitContent API
     * (the same mechanism Gboard uses for stickers/GIFs), if the app declares support for it.
     */
    private suspend fun insertContentFile(file: File, mimeType: String): Result<Unit> =
        withContext(Dispatchers.Main) {
            val ic = currentInputConnection
                ?: return@withContext Result.failure(IllegalStateException("No input connection"))
            val editorInfo = currentInputEditorInfo
                ?: return@withContext Result.failure(IllegalStateException("No editor info"))

            val supportedMimeTypes = EditorInfoCompat.getContentMimeTypes(editorInfo)
            val supported = supportedMimeTypes.any { ClipDescription.compareMimeTypes(mimeType, it) }
            if (!supported) {
                return@withContext Result.failure(
                    UnsupportedOperationException("This app doesn't support inline images"),
                )
            }

            val uri = FileProvider.getUriForFile(this@KeyboardService, "$packageName.fileprovider", file)
            val inputContentInfo = InputContentInfoCompat(uri, ClipDescription("content", arrayOf(mimeType)), null)
            val success = InputConnectionCompat.commitContent(
                ic,
                editorInfo,
                inputContentInfo,
                InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
                null,
            )
            if (success) Result.success(Unit) else Result.failure(IOException("commitContent failed"))
        }
}
