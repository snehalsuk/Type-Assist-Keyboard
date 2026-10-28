package com.aikeyboard.assistant.ime

import android.os.Build
import android.os.SystemClock
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private enum class LayoutMode { Letters, Symbols, Emoji }

/** Once = capitalize only the next letter; Locked = caps lock (double-tap shift). */
private enum class ShiftState { Off, Once, Locked }

/** Two shift taps closer together than this turn on caps lock. */
private const val SHIFT_DOUBLE_TAP_MS = 300L

private enum class CustomTarget(val placeholder: String) {
    Tone("Type a tone, then tap the check"),
    Prompt("Type an instruction, then tap the check"),
}

/** The docked feature panels reachable from the side rail once one is open. */
private enum class AiPanel(val label: String, val icon: String) {
    Grammar("Fix Grammar", "✓"),
    Tone("Change Tone", "✨"),
    More("AI Tools", "AI"),
    Translate("Translate", "🌐"),
    Clipboard("Clipboard", "📋"),
}

/** One or more labeled rewrite candidates shown as tappable cards under a panel. */
private data class PanelResult(val options: List<Pair<String, String>>)

private sealed class ToolbarState {
    object Idle : ToolbarState()
    object Listening : ToolbarState()
    data class Error(val message: String) : ToolbarState()
    data class CustomCapture(val target: CustomTarget, val text: String) : ToolbarState()
    data class Panel(
        val panel: AiPanel,
        val isLoading: Boolean = false,
        val result: PanelResult? = null,
    ) : ToolbarState()
}

/** Scales key height with the keyboard's actual width so it reads sensibly on phones and tablets. */
private val LocalKeyHeight = compositionLocalOf { 46.dp }

/** The active color/font theme, applied everywhere via CompositionLocal instead of prop-drilling. */
private val LocalKeyboardVisualTheme = compositionLocalOf {
    KeyboardVisualTheme(KeyboardThemes.first(), KeyboardFont.Default, 1f)
}

@Composable
fun KeyboardScreen(
    callbacks: KeyboardCallbacks,
    theme: KeyboardVisualTheme,
    imeAction: Int,
    screenHeightDp: Int,
    inputSession: Int,
) {
    val onKeyAction = callbacks.onKeyAction
    val onGetFieldText = callbacks.onGetFieldText
    val onReplaceFieldText = callbacks.onReplaceFieldText
    val onRunAiAction = callbacks.onRunAiAction
    val onSwitchKeyboard = callbacks.onSwitchKeyboard
    val onGetSuggestions = callbacks.onGetSuggestions
    val onPickSuggestion = callbacks.onPickSuggestion
    val onStartSpeech = callbacks.onStartSpeech
    val onGetClipboardHistory = callbacks.onGetClipboardHistory
    val onClearClipboardHistory = callbacks.onClearClipboardHistory
    val onOpenSettings = callbacks.onOpenSettings

    var shiftState by remember { mutableStateOf(ShiftState.Off) }
    var lastShiftTapAt by remember { mutableStateOf(0L) }
    var layoutMode by remember { mutableStateOf(LayoutMode.Letters) }
    var toolbarState by remember { mutableStateOf<ToolbarState>(ToolbarState.Idle) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var clipboardItems by remember { mutableStateOf<List<String>>(emptyList()) }
    var speechJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    // Re-derives one-shot shift from the field after every edit: on at a sentence start, off
    // otherwise (which is also what drops a manual one-shot shift after its letter). Caps lock
    // is left alone.
    fun refreshAutoShift() {
        if (shiftState != ShiftState.Locked) {
            shiftState = if (callbacks.onShouldAutoCapitalize()) ShiftState.Once else ShiftState.Off
        }
    }

    LaunchedEffect(inputSession) { refreshAutoShift() }

    fun openClipboardPanel() {
        clipboardItems = onGetClipboardHistory()
        toolbarState = ToolbarState.Panel(AiPanel.Clipboard)
    }

    fun clearClipboardHistory() {
        onClearClipboardHistory()
        clipboardItems = emptyList()
    }

    fun pasteClip(text: String) {
        onKeyAction(KeyAction.Text(text))
        toolbarState = ToolbarState.Idle
    }

    LaunchedEffect(toolbarState) {
        if (toolbarState is ToolbarState.Error) {
            delay(2000)
            toolbarState = ToolbarState.Idle
        }
    }

    fun runPanelAction(panel: AiPanel, action: AiAction, resultLabel: String) {
        val text = onGetFieldText()
        if (text.isBlank()) {
            toolbarState = ToolbarState.Error("Nothing to work with")
            return
        }
        toolbarState = ToolbarState.Panel(panel, isLoading = true)
        scope.launch {
            val result = onRunAiAction(action, text)
            toolbarState = result.fold(
                onSuccess = { ToolbarState.Panel(panel, result = PanelResult(listOf(resultLabel to it))) },
                onFailure = { ToolbarState.Error("Couldn't reach AI") },
            )
        }
    }

    fun runGrammarFix() {
        val text = onGetFieldText()
        if (text.isBlank()) {
            toolbarState = ToolbarState.Error("Nothing to work with")
            return
        }
        toolbarState = ToolbarState.Panel(AiPanel.Grammar, isLoading = true)
        scope.launch {
            val result = onRunAiAction(AiAction.FixGrammar, text)
            result.onSuccess(onReplaceFieldText)
            toolbarState = result.fold(
                onSuccess = { ToolbarState.Idle },
                onFailure = { ToolbarState.Error("Couldn't reach AI") },
            )
        }
    }

    fun runReplyAction() {
        val text = onGetFieldText()
        if (text.isBlank()) {
            toolbarState = ToolbarState.Error("Nothing to work with")
            return
        }
        toolbarState = ToolbarState.Panel(AiPanel.More, isLoading = true)
        scope.launch {
            val result = onRunAiAction(AiAction.SmartReply, text)
            toolbarState = result.fold(
                onSuccess = { raw ->
                    val options = parseReplyVariants(raw)
                    if (options.isEmpty()) {
                        ToolbarState.Error("Couldn't reach AI")
                    } else {
                        ToolbarState.Panel(AiPanel.More, result = PanelResult(options))
                    }
                },
                onFailure = { ToolbarState.Error("Couldn't reach AI") },
            )
        }
    }

    fun runSpeech() {
        toolbarState = ToolbarState.Listening
        speechJob = scope.launch {
            val result = onStartSpeech()
            toolbarState = result.fold(
                onSuccess = { text ->
                    val current = onGetFieldText()
                    onReplaceFieldText(if (current.isBlank()) text else "$current $text")
                    ToolbarState.Idle
                },
                onFailure = { ToolbarState.Error("Couldn't use microphone") },
            )
        }
    }

    // Cancelling the job unwinds SpeechInputHelper's callbackFlow through its awaitClose block,
    // which stops and destroys the SpeechRecognizer -- so this is a clean abort, not a leak.
    fun cancelSpeech() {
        speechJob?.cancel()
        speechJob = null
        toolbarState = ToolbarState.Idle
    }

    fun submitCustomCapture(capture: ToolbarState.CustomCapture) {
        if (capture.text.isBlank()) return
        when (capture.target) {
            CustomTarget.Tone -> runPanelAction(AiPanel.Tone, AiAction.Tone(capture.text), capture.text)
            CustomTarget.Prompt -> runPanelAction(AiPanel.More, AiAction.CustomPrompt(capture.text), "Custom")
        }
    }

    fun applyResult(text: String) {
        onReplaceFieldText(text)
        toolbarState = ToolbarState.Idle
    }

    // While capturing free-form text (a custom tone or prompt), key presses fill that buffer
    // instead of typing into the app's field -- the keyboard has no other way to take dictation
    // from the user since it IS the input surface.
    val routedKeyAction: (KeyAction) -> Unit = routed@{ action ->
        val capture = toolbarState as? ToolbarState.CustomCapture
        if (capture == null) {
            onKeyAction(action)
            suggestions = onGetSuggestions()
            refreshAutoShift()
            return@routed
        }
        when (action) {
            is KeyAction.Character -> {
                toolbarState = capture.copy(text = capture.text + action.char)
                if (shiftState == ShiftState.Once) shiftState = ShiftState.Off
            }
            is KeyAction.Text -> toolbarState = capture.copy(text = capture.text + action.text)
            KeyAction.Space -> toolbarState = capture.copy(text = capture.text + " ")
            KeyAction.Backspace -> toolbarState = capture.copy(text = capture.text.dropLast(1))
            KeyAction.Enter -> submitCustomCapture(capture)
            KeyAction.Shift -> Unit
        }
    }

    val onPickSuggestionAndRefresh: (String) -> Unit = { word ->
        onPickSuggestion(word)
        suggestions = onGetSuggestions()
        refreshAutoShift()
    }

    val showError: (String) -> Unit = { msg -> toolbarState = ToolbarState.Error(msg) }
    val isAiPanelOpen = toolbarState is ToolbarState.Panel || toolbarState is ToolbarState.CustomCapture

    val screenHeightDpValue = screenHeightDp.dp

    CompositionLocalProvider(LocalKeyboardVisualTheme provides theme) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // Width alone isn't enough: on a wide-but-short screen (landscape phones, and any
            // BoxWithConstraints whose height constraint the IME window leaves effectively
            // unbounded) sizing purely off maxWidth drives keyHeight straight to its max clamp,
            // and four-plus rows of 64dp keys can end up covering nearly the whole screen. Cap
            // the keyboard's total height to a fraction of the actual device screen height too,
            // and use whichever of the two constraints is smaller.
            val maxKeyboardHeight = screenHeightDpValue * 0.55f
            val heightBasedKeyHeight = ((maxKeyboardHeight - 90.dp) / 4.3f).coerceAtLeast(0.dp)
            val widthBasedKeyHeight = maxWidth / 9.5f
            // coerceAtMost only -- a coerceIn(30.dp, ...) floor would force keyHeight back up past
            // whatever heightBasedKeyHeight computed, which defeats the whole point of that budget
            // on short/landscape screens: it'd silently blow through the 55%-of-screen cap above
            // and push rows (backspace included) outside the visible/touchable keyboard area.
            val keyHeight = minOf(widthBasedKeyHeight, heightBasedKeyHeight).coerceAtMost(64.dp)
            theme.backgroundImagePath?.let { path ->
                AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
            CompositionLocalProvider(LocalKeyHeight provides keyHeight) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Only paint the solid theme color when there's no background image --
                        // painting it on top at any opacity would just hide the image again, and
                        // the keys/panels underneath already have their own opaque backgrounds
                        // for legibility, so the photo doesn't need a tint to stay readable.
                        .then(
                            if (theme.backgroundImagePath == null) {
                                Modifier.background(theme.colors.background)
                            } else {
                                Modifier
                            },
                        )
                        .navigationBarsPadding()
                        .padding(4.dp),
                ) {
                    if (isAiPanelOpen) {
                        // The rail wants room for 6 x 44dp icons + gaps (~304dp) regardless of how
                        // small keyHeight scales on narrow screens, but never beyond the same
                        // screen-height budget the key rows respect -- the rail itself scrolls if
                        // that still isn't enough room for all its buttons.
                        val panelHeight = maxOf(keyHeight * 4 + 28.dp, 304.dp).coerceAtMost(maxKeyboardHeight)
                        AiBottomRow(onSwitchKeyboard = onSwitchKeyboard, onMicTap = ::runSpeech)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(panelHeight),
                        ) {
                            AiRail(
                                activePanel = (toolbarState as? ToolbarState.Panel)?.panel,
                                onSelectGrammar = ::runGrammarFix,
                                onSelectTone = { toolbarState = ToolbarState.Panel(AiPanel.Tone) },
                                onSelectMore = { toolbarState = ToolbarState.Panel(AiPanel.More) },
                                onSelectTranslate = { toolbarState = ToolbarState.Panel(AiPanel.Translate) },
                                onSelectClipboard = ::openClipboardPanel,
                                onClose = { toolbarState = ToolbarState.Idle },
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                when (val state = toolbarState) {
                                    is ToolbarState.Panel -> AiPanelContent(
                                        state = state,
                                        clipboardItems = clipboardItems,
                                        onRunTone = { style -> runPanelAction(AiPanel.Tone, AiAction.Tone(style), style) },
                                        onRunTranslate = { lang ->
                                            runPanelAction(AiPanel.Translate, AiAction.Translate(lang), lang)
                                        },
                                        onRunMoreAction = { action, label -> runPanelAction(AiPanel.More, action, label) },
                                        onRunReply = ::runReplyAction,
                                        onStartCustomTone = {
                                            toolbarState = ToolbarState.CustomCapture(CustomTarget.Tone, "")
                                        },
                                        onStartCustomPrompt = {
                                            toolbarState = ToolbarState.CustomCapture(CustomTarget.Prompt, "")
                                        },
                                        onApplyResult = ::applyResult,
                                        onPasteClip = ::pasteClip,
                                        onClearClipboard = ::clearClipboardHistory,
                                    )
                                    is ToolbarState.CustomCapture -> CustomCapturePanel(
                                        state = state,
                                        onSubmit = { submitCustomCapture(state) },
                                        onCancel = { toolbarState = ToolbarState.Idle },
                                    )
                                    else -> Unit
                                }
                            }
                        }
                    } else {
                        // key(screenHeightDp) forces this entire block to fully dispose and
                        // recompose from scratch on every rotation, instead of incrementally
                        // recomposing in place. On-device testing found individual keys (not
                        // consistently the same one) can stop receiving touches after a rotation:
                        // Android confirms it still delivers the touch to this window, but
                        // Compose's own pointer input never sees a press for that node. The IME's
                        // window goes through a multi-frame synchronized resize on rotation (see
                        // onConfigurationChanged below) since there's no Activity smoothing that
                        // over the way there would be for a normal app screen, and a pointer-input
                        // node that's mid-attach when that resize lands can apparently end up
                        // wired to stale geometry. Rebuilding the nodes outright after every
                        // rotation sidesteps that rather than trying to keep incrementally-patched
                        // nodes consistent with it.
                        key(screenHeightDp) {
                            FeatureIconRow(
                                state = toolbarState,
                                onOpenTranslate = { toolbarState = ToolbarState.Panel(AiPanel.Translate) },
                                onOpenTone = { toolbarState = ToolbarState.Panel(AiPanel.Tone) },
                                onOpenMore = { toolbarState = ToolbarState.Panel(AiPanel.More) },
                                onOpenClipboard = ::openClipboardPanel,
                                onSwitchKeyboard = onSwitchKeyboard,
                                onOpenSettings = onOpenSettings,
                                onMicTap = ::runSpeech,
                                onCancelSpeech = ::cancelSpeech,
                            )
                            if (suggestions.isNotEmpty() && toolbarState is ToolbarState.Idle) {
                                SuggestionRow(suggestions = suggestions, onPick = onPickSuggestionAndRefresh)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            when (layoutMode) {
                                LayoutMode.Letters -> LetterKeys(
                                    shiftState = shiftState,
                                    onShiftToggle = {
                                        val now = SystemClock.uptimeMillis()
                                        shiftState = when {
                                            shiftState == ShiftState.Once &&
                                                now - lastShiftTapAt < SHIFT_DOUBLE_TAP_MS -> ShiftState.Locked
                                            shiftState == ShiftState.Off -> ShiftState.Once
                                            else -> ShiftState.Off
                                        }
                                        lastShiftTapAt = now
                                    },
                                    onKeyAction = routedKeyAction,
                                )
                                LayoutMode.Symbols -> SymbolKeys(onKeyAction = routedKeyAction)
                                LayoutMode.Emoji -> EmojiPanel(
                                    callbacks = callbacks,
                                    onKeyAction = routedKeyAction,
                                    onError = showError,
                                )
                            }
                            BottomRow(
                                layoutMode = layoutMode,
                                onLayoutToggle = {
                                    layoutMode = if (layoutMode == LayoutMode.Symbols) {
                                        LayoutMode.Letters
                                    } else {
                                        LayoutMode.Symbols
                                    }
                                },
                                onEmojiToggle = {
                                    layoutMode = if (layoutMode == LayoutMode.Emoji) {
                                        LayoutMode.Letters
                                    } else {
                                        LayoutMode.Emoji
                                    }
                                },
                                onKeyAction = routedKeyAction,
                                imeAction = imeAction,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Persistent row of feature icon buttons shown above the keys, matching the reference design. */
@Composable
private fun FeatureIconRow(
    state: ToolbarState,
    onOpenTranslate: () -> Unit,
    onOpenTone: () -> Unit,
    onOpenMore: () -> Unit,
    onOpenClipboard: () -> Unit,
    onSwitchKeyboard: () -> Unit,
    onOpenSettings: () -> Unit,
    onMicTap: () -> Unit,
    onCancelSpeech: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        when (state) {
            is ToolbarState.Listening -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(start = 4.dp).height(16.dp),
                        color = Color(0xFFE57373),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    MicGlyphIcon(color = Color(0xFFA0A0A0))
                    Spacer(Modifier.width(6.dp))
                    Text(text = "Listening...", color = Color(0xFFA0A0A0), fontSize = 13.sp)
                }
                FeatureIconButton(icon = "✕", background = Color(0xFF3A3A3A), onClick = onCancelSpeech)
            }
            is ToolbarState.Error -> Text(
                text = state.message,
                color = Color(0xFFE57373),
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
            else -> {
                // Each cluster gets its own spacedBy so icons inside it are evenly gapped --
                // a single weighted Spacer between clusters would otherwise soak up all the
                // "extra" space that Arrangement.SpaceBetween had to distribute, leaving these
                // icons packed edge-to-edge with no visible gap.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    FeatureIconButton(icon = "🌐", background = Color(0xFF2F7DE1), onClick = onOpenTranslate)
                    FeatureIconButton(icon = "✨", background = Color(0xFF6C5CE7), onClick = onOpenTone)
                    FeatureIconButton(icon = "AI", background = Color(0xFF3A3A3A), onClick = onOpenMore)
                    IconDivider()
                    ClipboardIconButton(onClick = onOpenClipboard)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    FeatureIconButton(icon = "⌨", background = Color(0xFF2A2A2A), onClick = onSwitchKeyboard)
                    FeatureIconButton(icon = "⚙", background = Color(0xFF2A2A2A), onClick = onOpenSettings)
                    MicIconButton(background = Color(0xFFE57373), onClick = onMicTap)
                }
            }
        }
    }
}

@Composable
private fun FeatureIconButton(icon: String, background: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = icon, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** Thin separator between the colored AI action icons and the plain utility icons, matching the reference. */
@Composable
private fun IconDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .width(1.dp)
            .height(22.dp)
            .background(Color.White.copy(alpha = 0.2f)),
    )
}

/** Flat (no background) clipboard glyph, hand-drawn to match the reference's outline icon style. */
@Composable
private fun ClipboardIconButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val stroke = Stroke(width = 1.6.dp.toPx())
            val bodyTop = size.height * 0.16f
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(0f, bodyTop),
                size = Size(size.width, size.height - bodyTop),
                cornerRadius = CornerRadius(size.width * 0.16f),
                style = stroke,
            )
            val tabWidth = size.width * 0.5f
            val tabHeight = bodyTop * 1.4f
            drawRoundRect(
                color = Color.White,
                topLeft = Offset((size.width - tabWidth) / 2f, 0f),
                size = Size(tabWidth, tabHeight),
                cornerRadius = CornerRadius(tabHeight * 0.35f),
                style = stroke,
            )
        }
    }
}

/** Microphone glyph (capsule head + stand + base), hand-drawn to replace the plain emoji. */
private fun DrawScope.drawMicGlyph(color: Color) {
    val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
    val capsuleWidth = size.width * 0.34f
    val capsuleHeight = size.height * 0.46f
    val capsuleLeft = (size.width - capsuleWidth) / 2f
    val capsuleTop = size.height * 0.04f
    drawRoundRect(
        color = color,
        topLeft = Offset(capsuleLeft, capsuleTop),
        size = Size(capsuleWidth, capsuleHeight),
        cornerRadius = CornerRadius(capsuleWidth / 2f),
    )
    val standRadius = size.width * 0.28f
    val standCenterX = size.width / 2f
    val standTop = capsuleTop + capsuleHeight - size.height * 0.08f
    drawArc(
        color = color,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(standCenterX - standRadius, standTop),
        size = Size(standRadius * 2, standRadius * 2),
        style = stroke,
    )
    val standBottom = standTop + standRadius * 2
    val postBottom = standBottom + size.height * 0.14f
    drawLine(
        color = color,
        start = Offset(standCenterX, standBottom),
        end = Offset(standCenterX, postBottom),
        strokeWidth = stroke.width,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color,
        start = Offset(standCenterX - size.width * 0.2f, postBottom),
        end = Offset(standCenterX + size.width * 0.2f, postBottom),
        strokeWidth = stroke.width,
        cap = StrokeCap.Round,
    )
}

@Composable
private fun MicGlyphIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp)) { drawMicGlyph(color) }
}

@Composable
private fun MicIconButton(background: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(18.dp)) { drawMicGlyph(Color.White) }
    }
}

/** Key-styled mic button matching [KeyButton]'s look, for rows that use the keys' own theme colors. */
@Composable
private fun MicKeyButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val theme = LocalKeyboardVisualTheme.current
    Box(
        modifier = modifier
            .height(LocalKeyHeight.current)
            .background(color = theme.colors.key, shape = RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(20.dp)) { drawMicGlyph(theme.colors.text) }
    }
}

@Composable
private fun AiRail(
    activePanel: AiPanel?,
    onSelectGrammar: () -> Unit,
    onSelectTone: () -> Unit,
    onSelectMore: () -> Unit,
    onSelectTranslate: () -> Unit,
    onSelectClipboard: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(46.dp)
            .fillMaxHeight()
            // On short/landscape screens panelHeight can end up smaller than the rail's natural
            // content height (6 x 44dp buttons + gaps) -- scroll instead of silently clipping the
            // last button(s) off the bottom.
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RailButton(icon = "ABC", selected = false, background = Color(0xFF2A2A2A), onClick = onClose)
        RailButton(
            icon = AiPanel.Grammar.icon,
            selected = activePanel == AiPanel.Grammar,
            background = Color(0xFF2F7DE1),
            onClick = onSelectGrammar,
        )
        RailButton(
            icon = AiPanel.Tone.icon,
            selected = activePanel == AiPanel.Tone,
            background = Color(0xFF6C5CE7),
            onClick = onSelectTone,
        )
        RailButton(
            icon = AiPanel.More.icon,
            selected = activePanel == AiPanel.More,
            background = Color(0xFF3A3A3A),
            onClick = onSelectMore,
        )
        RailButton(
            icon = AiPanel.Translate.icon,
            selected = activePanel == AiPanel.Translate,
            background = Color(0xFF2F7DE1),
            onClick = onSelectTranslate,
        )
        RailButton(
            icon = AiPanel.Clipboard.icon,
            selected = activePanel == AiPanel.Clipboard,
            background = Color(0xFF3A3A3A),
            onClick = onSelectClipboard,
        )
    }
}

@Composable
private fun RailButton(icon: String, selected: Boolean, background: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (selected) background else background.copy(alpha = 0.5f))
            .then(
                if (selected) {
                    Modifier.border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = icon,
            color = Color.White,
            fontSize = if (icon.length > 2) 11.sp else 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AiPanelContent(
    state: ToolbarState.Panel,
    clipboardItems: List<String>,
    onRunTone: (String) -> Unit,
    onRunTranslate: (String) -> Unit,
    onRunMoreAction: (AiAction, String) -> Unit,
    onRunReply: () -> Unit,
    onStartCustomTone: () -> Unit,
    onStartCustomPrompt: () -> Unit,
    onApplyResult: (String) -> Unit,
    onPasteClip: (String) -> Unit,
    onClearClipboard: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF161616))
            .padding(10.dp),
    ) {
        Text(text = state.panel.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        when (state.panel) {
            AiPanel.Grammar -> {
                if (state.isLoading) {
                    LoadingRow()
                } else {
                    Text(
                        text = "Fixing grammar and applying it automatically...",
                        color = Color(0xFFA0A0A0),
                        fontSize = 13.sp,
                    )
                }
            }
            AiPanel.Tone -> {
                ChipScrollRow {
                    ToneStyles.forEach { style ->
                        ToolbarChip(label = style, onClick = { onRunTone(style) })
                    }
                    ToolbarChip(label = "Custom", onClick = onStartCustomTone)
                }
                Spacer(Modifier.height(8.dp))
                ResultArea(state = state, onApply = onApplyResult)
            }
            AiPanel.Translate -> {
                ChipScrollRow {
                    TranslateLanguages.forEach { language ->
                        ToolbarChip(label = language, onClick = { onRunTranslate(language) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                ResultArea(state = state, onApply = onApplyResult)
            }
            AiPanel.Clipboard -> {
                if (clipboardItems.isEmpty()) {
                    Text(text = "Nothing copied yet", color = Color(0xFF808080), fontSize = 13.sp)
                } else {
                    ToolbarChip(label = "Clear all", onClick = onClearClipboard)
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        clipboardItems.forEach { item ->
                            ResultCard(label = "", text = item, showLabel = false, onClick = { onPasteClip(item) })
                        }
                    }
                }
            }
            AiPanel.More -> {
                ChipScrollRow {
                    ToolbarChip(label = "Reply", onClick = onRunReply)
                    ToolbarChip(label = "Summarize", onClick = { onRunMoreAction(AiAction.Summarize, "Summarize") })
                    ToolbarChip(label = "Humanize", onClick = { onRunMoreAction(AiAction.Humanize, "Humanize") })
                    ToolbarChip(label = "Emojify", onClick = { onRunMoreAction(AiAction.Emojify, "Emojify") })
                    ToolbarChip(label = "Caption", onClick = { onRunMoreAction(AiAction.Caption, "Caption") })
                    ToolbarChip(label = "Custom Prompt", onClick = onStartCustomPrompt)
                }
                Spacer(Modifier.height(8.dp))
                ResultArea(state = state, onApply = onApplyResult)
            }
        }
    }
}

@Composable
private fun ResultArea(state: ToolbarState.Panel, onApply: (String) -> Unit) {
    if (state.isLoading) {
        LoadingRow()
        return
    }
    val result = state.result
    if (result == null) {
        Text(text = "Pick an option above", color = Color(0xFF808080), fontSize = 13.sp)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        result.options.forEach { (label, text) ->
            ResultCard(label = label, text = text, showLabel = result.options.size > 1, onClick = { onApply(text) })
        }
    }
}

@Composable
private fun ResultCard(label: String, text: String, showLabel: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF2A2A2A))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        if (showLabel) {
            Text(text = label, color = Color(0xFF8AB4FF), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
        }
        Text(
            text = text,
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LoadingRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.height(16.dp), color = Color.White, strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(text = "Thinking...", color = Color(0xFFA0A0A0), fontSize = 13.sp)
    }
}

@Composable
private fun ChipScrollRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun CustomCapturePanel(state: ToolbarState.CustomCapture, onSubmit: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF161616))
            .padding(10.dp),
    ) {
        Text(
            text = if (state.target == CustomTarget.Tone) "Custom Tone" else "Custom Prompt",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = state.text.ifEmpty { state.target.placeholder },
            color = if (state.text.isEmpty()) Color(0xFF808080) else Color.White,
            fontSize = 14.sp,
        )
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolbarChip(label = "Cancel", onClick = onCancel)
            ToolbarChip(label = "Submit ✓", onClick = onSubmit)
        }
    }
}

@Composable
private fun AiBottomRow(onSwitchKeyboard: () -> Unit, onMicTap: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // "⌨", not "🌐" -- the Translate rail button right next to this row already owns the
        // globe glyph, and reusing it here for switch-keyboard read as the same icon shown twice.
        KeyButton(label = "⌨", modifier = Modifier.weight(1f), onClick = onSwitchKeyboard)
        Spacer(Modifier.weight(3f))
        MicKeyButton(modifier = Modifier.weight(1f), onClick = onMicTap)
    }
}

@Composable
private fun SuggestionRow(suggestions: List<String>, onPick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        suggestions.forEach { word ->
            ToolbarChip(label = word, onClick = { onPick(word) })
        }
    }
}

@Composable
private fun ToolbarChip(label: String, onClick: () -> Unit, selected: Boolean = false) {
    val theme = LocalKeyboardVisualTheme.current
    Box(
        modifier = Modifier
            .height(28.dp)
            .background(
                color = if (selected) theme.colors.accent else Color(0xFF3A3A3A),
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun LetterKeys(
    shiftState: ShiftState,
    onShiftToggle: () -> Unit,
    onKeyAction: (KeyAction) -> Unit,
) {
    val shiftEnabled = shiftState != ShiftState.Off
    val rows = KeyboardLayout.letterRows
    KeyRow(keys = rows[0], shiftEnabled = shiftEnabled, onKeyAction = onKeyAction)
    KeyRow(keys = rows[1], shiftEnabled = shiftEnabled, onKeyAction = onKeyAction)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        KeyButton(
            label = if (shiftState == ShiftState.Locked) "⇪" else "⇧",
            modifier = Modifier.weight(1.5f),
            highlighted = shiftEnabled,
            onClick = onShiftToggle,
        )
        rows[2].forEach { char ->
            KeyButton(
                label = if (shiftEnabled) char.uppercaseChar().toString() else char.toString(),
                modifier = Modifier.weight(1f),
                onClick = {
                    val emitted = if (shiftEnabled) char.uppercaseChar() else char
                    onKeyAction(KeyAction.Character(emitted))
                },
            )
        }
        KeyButton(
            label = "⌫",
            modifier = Modifier.weight(1.5f),
            repeatable = true,
            onClick = { onKeyAction(KeyAction.Backspace) },
        )
    }
}

@Composable
private fun KeyRow(
    keys: List<Char>,
    shiftEnabled: Boolean,
    onKeyAction: (KeyAction) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        keys.forEach { char ->
            KeyButton(
                label = if (shiftEnabled) char.uppercaseChar().toString() else char.toString(),
                modifier = Modifier.weight(1f),
                onClick = {
                    val emitted = if (shiftEnabled) char.uppercaseChar() else char
                    onKeyAction(KeyAction.Character(emitted))
                },
            )
        }
    }
}

@Composable
private fun SymbolKeys(onKeyAction: (KeyAction) -> Unit) {
    val rows = KeyboardLayout.symbolRows
    SymbolRow(keys = rows[0], onKeyAction = onKeyAction)
    SymbolRow(keys = rows[1], onKeyAction = onKeyAction)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        rows[2].forEach { char ->
            KeyButton(
                label = char.toString(),
                modifier = Modifier.weight(1f),
                onClick = { onKeyAction(KeyAction.Character(char)) },
            )
        }
        KeyButton(
            label = "⌫",
            modifier = Modifier.weight(1.8f),
            repeatable = true,
            onClick = { onKeyAction(KeyAction.Backspace) },
        )
    }
}

@Composable
private fun SymbolRow(keys: List<Char>, onKeyAction: (KeyAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        keys.forEach { char ->
            KeyButton(
                label = char.toString(),
                modifier = Modifier.weight(1f),
                onClick = { onKeyAction(KeyAction.Character(char)) },
            )
        }
    }
}

private enum class EmojiTab(val label: String) {
    Emoji("Emoji"),
    Recent("Recent"),
    Stickers("Stickers"),
    Gif("GIF"),
}

@Composable
private fun EmojiPanel(
    callbacks: KeyboardCallbacks,
    onKeyAction: (KeyAction) -> Unit,
    onError: (String) -> Unit,
) {
    var tab by remember { mutableStateOf(EmojiTab.Emoji) }
    var selectedCategory by remember { mutableStateOf(EmojiCategories.first()) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EmojiTab.entries.forEach { candidate ->
                ToolbarChip(label = candidate.label, selected = candidate == tab, onClick = { tab = candidate })
            }
        }
        if (tab == EmojiTab.Emoji) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EmojiCategories.forEach { category ->
                    ToolbarChip(
                        label = category.icon,
                        selected = category == selectedCategory,
                        onClick = { selectedCategory = category },
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        when (tab) {
            EmojiTab.Emoji -> EmojiGrid(selectedCategory.emoji) { emoji ->
                onKeyAction(KeyAction.Text(emoji))
                callbacks.onRecordEmojiUse(emoji)
            }
            EmojiTab.Recent -> {
                val recent = callbacks.onGetRecentEmoji()
                if (recent.isEmpty()) {
                    EmptyTabMessage("No recent emoji yet")
                } else {
                    EmojiGrid(recent) { emoji ->
                        onKeyAction(KeyAction.Text(emoji))
                        callbacks.onRecordEmojiUse(emoji)
                    }
                }
            }
            EmojiTab.Stickers -> StickerGrid(callbacks = callbacks, onError = onError)
            EmojiTab.Gif -> GifGrid(callbacks = callbacks, onError = onError)
        }
    }
}

@Composable
private fun EmojiGrid(items: List<String>, onTap: (String) -> Unit) {
    val keyHeight = LocalKeyHeight.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(8),
        modifier = Modifier
            .fillMaxWidth()
            .height(keyHeight * 3 + 24.dp),
        contentPadding = PaddingValues(4.dp),
    ) {
        items(items) { emoji ->
            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .height(40.dp)
                    .clickable { onTap(emoji) },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = emoji, fontSize = 22.sp)
            }
        }
    }
}

@Composable
private fun EmptyTabMessage(text: String) {
    val keyHeight = LocalKeyHeight.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(keyHeight * 3 + 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = Color(0xFF808080), fontSize = 13.sp)
    }
}

@Composable
private fun StickerGrid(callbacks: KeyboardCallbacks, onError: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var stickers by remember { mutableStateOf(callbacks.onGetStickers()) }

    Column {
        ToolbarChip(
            label = "+ Save from clipboard",
            onClick = {
                if (callbacks.onSaveStickerFromClipboard()) {
                    stickers = callbacks.onGetStickers()
                } else {
                    onError("No image on clipboard")
                }
            },
        )
        Spacer(Modifier.height(4.dp))
        if (stickers.isEmpty()) {
            EmptyTabMessage("No saved stickers yet")
        } else {
            val keyHeight = LocalKeyHeight.current
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(keyHeight * 2 + 16.dp),
                contentPadding = PaddingValues(4.dp),
            ) {
                items(stickers) { file ->
                    AsyncImage(
                        model = file,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(3.dp)
                            .height(60.dp)
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    callbacks.onInsertSticker(file).onFailure {
                                        onError(it.message ?: "Couldn't send sticker")
                                    }
                                }
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun GifGrid(callbacks: KeyboardCallbacks, onError: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var gifs by remember { mutableStateOf<List<GifResult>?>(null) }
    val context = LocalContext.current
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                // ImageDecoderDecoder uses Android's modern ImageDecoder API. The legacy
                // GifDecoder path (android.graphics.Movie) has a native heap-corruption bug
                // on some OEM builds that crashes the whole process while closing a decoded
                // GIF -- only fall back to it below API 28, where ImageDecoder doesn't exist.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    LaunchedEffect(Unit) {
        callbacks.onGetTrendingGifs()
            .onSuccess { gifs = it }
            .onFailure {
                onError("Couldn't load GIFs")
                gifs = emptyList()
            }
    }

    val loaded = gifs
    when {
        loaded == null -> EmptyTabMessage("Loading GIFs...")
        loaded.isEmpty() -> EmptyTabMessage("No GIFs available")
        else -> {
            val keyHeight = LocalKeyHeight.current
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(keyHeight * 3 + 24.dp),
                contentPadding = PaddingValues(4.dp),
            ) {
                items(loaded) { gif ->
                    AsyncImage(
                        model = gif.previewUrl,
                        imageLoader = imageLoader,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(3.dp)
                            .height(64.dp)
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    callbacks.onInsertGif(gif.fullUrl).onFailure {
                                        onError(it.message ?: "Couldn't send GIF")
                                    }
                                }
                            },
                    )
                }
            }
        }
    }
}

/** What the Enter key should show for the host field's requested action, e.g. search vs. go vs. send. */
private fun enterKeyLabel(imeAction: Int): String = when (imeAction) {
    EditorInfo.IME_ACTION_SEARCH -> "🔍"
    EditorInfo.IME_ACTION_GO -> "Go"
    EditorInfo.IME_ACTION_SEND -> "➤"
    EditorInfo.IME_ACTION_DONE -> "✓"
    EditorInfo.IME_ACTION_NEXT -> "→"
    EditorInfo.IME_ACTION_PREVIOUS -> "←"
    else -> "⏎"
}

@Composable
private fun BottomRow(
    layoutMode: LayoutMode,
    onLayoutToggle: () -> Unit,
    onEmojiToggle: () -> Unit,
    onKeyAction: (KeyAction) -> Unit,
    imeAction: Int,
) {
    // Standard Android convention: the spacebar shows the active input language instead of the
    // literal word "space" -- switching keyboards is reached from the feature icon row above.
    val spaceLabel = remember { java.util.Locale.getDefault().displayName }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        KeyButton(
            label = if (layoutMode == LayoutMode.Symbols) "ABC" else "?123",
            modifier = Modifier.weight(1.3f),
            compactLabel = true,
            onClick = onLayoutToggle,
        )
        KeyButton(
            label = if (layoutMode == LayoutMode.Emoji) "ABC" else "😊",
            modifier = Modifier.weight(1f),
            onClick = onEmojiToggle,
        )
        KeyButton(
            label = ",",
            modifier = Modifier.weight(1f),
            onClick = { onKeyAction(KeyAction.Character(',')) },
        )
        KeyButton(
            label = spaceLabel,
            modifier = Modifier.weight(3.7f),
            compactLabel = true,
            onClick = { onKeyAction(KeyAction.Space) },
        )
        KeyButton(
            label = ".",
            modifier = Modifier.weight(1f),
            onClick = { onKeyAction(KeyAction.Character('.')) },
        )
        KeyButton(
            label = enterKeyLabel(imeAction),
            modifier = Modifier.weight(1.3f),
            highlighted = true,
            compactLabel = imeAction == EditorInfo.IME_ACTION_GO,
            onClick = { onKeyAction(KeyAction.Enter) },
        )
    }
}

@Composable
private fun KeyButton(
    label: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    repeatable: Boolean = false,
    compactLabel: Boolean = false,
    onClick: () -> Unit,
) {
    val theme = LocalKeyboardVisualTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    if (repeatable) {
        // Built on Modifier.clickable's interactionSource rather than a hand-rolled
        // pointerInput+detectTapGestures gesture detector: the IME's ComposeView is attached to
        // its own window with no Activity driving configuration changes through it (see
        // onConfigurationChanged below), and an in-flight raw detectTapGestures press can get
        // orphaned by a rotation -- its tryAwaitRelease() then hangs forever waiting for an
        // up/cancel event Compose never delivers, permanently wedging that key until the
        // keyboard is torn down and recreated. Modifier.clickable's own press handling proved
        // robust across rotation (every other key keeps working), so repeat-hold rides on the
        // same PressInteraction stream instead of a bespoke gesture detector.
        LaunchedEffect(interactionSource) {
            interactionSource.interactions.collectLatest { interaction ->
                if (interaction !is PressInteraction.Press) return@collectLatest
                onClick()
                delay(400)
                val heldSince = System.currentTimeMillis()
                while (isActive) {
                    onClick()
                    val held = System.currentTimeMillis() - heldSince
                    val interval = when {
                        held < 1100L -> 70L
                        held < 2500L -> 45L
                        else -> 25L
                    }
                    delay(interval)
                }
            }
        }
    }
    Box(
        modifier = modifier
            .height(LocalKeyHeight.current)
            .background(
                color = if (highlighted) theme.colors.accent else theme.colors.key,
                shape = RoundedCornerShape(10.dp),
            )
            .then(
                if (repeatable) {
                    // The LaunchedEffect above already fires onClick on press and repeats it;
                    // firing it again here on a completed click would double-delete on every tap.
                    Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = {})
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            )
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = theme.colors.text,
            fontSize = if (compactLabel) (11 * theme.fontScale).sp else (16 * theme.fontScale).sp,
            fontFamily = theme.font.family,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
