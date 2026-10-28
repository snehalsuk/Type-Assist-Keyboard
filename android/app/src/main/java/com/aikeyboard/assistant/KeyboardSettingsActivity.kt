package com.aikeyboard.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aikeyboard.assistant.ime.KeyboardFont
import com.aikeyboard.assistant.ime.KeyboardFontSize
import com.aikeyboard.assistant.ime.KeyboardTheme
import com.aikeyboard.assistant.ime.KeyboardThemes
import com.aikeyboard.assistant.ime.ThemeStore
import java.io.File

/** Lets the user pick the keyboard's color theme, font, and text size. Applied on next keyboard show. */
class KeyboardSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = ThemeStore(applicationContext)

        setContent {
            var theme by remember { mutableStateOf(store.currentTheme()) }
            var font by remember { mutableStateOf(store.currentFont()) }
            var fontSize by remember { mutableStateOf(store.currentFontSize()) }
            var backgroundImagePath by remember { mutableStateOf(store.currentBackgroundImagePath()) }

            // The system photo picker needs no storage permission -- it grants read access to
            // just the picked item. We copy its bytes into our own file since the returned
            // content:// URI's access can be revoked once this activity finishes. Each pick gets
            // a fresh filename rather than overwriting a fixed one -- Coil caches image requests
            // by their model (the File/path), so reusing the same path would keep showing the
            // previously cached bitmap even after the file's contents changed underneath it.
            val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) {
                    runCatching {
                        val target = File(applicationContext.filesDir, "keyboard_background_${System.currentTimeMillis()}.jpg")
                        contentResolver.openInputStream(uri)?.use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        }
                        target.absolutePath
                    }.onSuccess { path ->
                        backgroundImagePath?.let { File(it).delete() }
                        store.setBackgroundImagePath(path)
                        backgroundImagePath = path
                    }
                }
            }

            MaterialTheme {
                Surface(color = Color(0xFF141414)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                    ) {
                        Text(
                            text = "Keyboard Settings",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Applies the next time the keyboard opens.",
                            color = Color(0xFFA0A0A0),
                            style = MaterialTheme.typography.bodySmall,
                        )

                        SectionTitle("Theme")
                        ThemeGrid(selected = theme) {
                            theme = it
                            store.setTheme(it)
                        }

                        SectionTitle("Background Image")
                        BackgroundImageSection(
                            imagePath = backgroundImagePath,
                            onChooseImage = {
                                pickImage.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            onClearImage = {
                                backgroundImagePath?.let { File(it).delete() }
                                store.setBackgroundImagePath(null)
                                backgroundImagePath = null
                            },
                        )

                        SectionTitle("Font")
                        ChipRow(
                            options = KeyboardFont.entries,
                            labelOf = { it.label },
                            selected = font,
                        ) {
                            font = it
                            store.setFont(it)
                        }

                        SectionTitle("Text Size")
                        ChipRow(
                            options = KeyboardFontSize.entries,
                            labelOf = { it.label },
                            selected = fontSize,
                        ) {
                            fontSize = it
                            store.setFontSize(it)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp),
    )
}

@Composable
private fun BackgroundImageSection(imagePath: String?, onChooseImage: () -> Unit, onClearImage: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (imagePath != null) {
            AsyncImage(
                model = File(imagePath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
            )
        }
        SettingsButton(label = "Choose Image", onClick = onChooseImage)
        if (imagePath != null) {
            SettingsButton(label = "Clear", onClick = onClearImage)
        }
    }
}

@Composable
private fun SettingsButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF2A2A2A))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text = label, color = Color.White)
    }
}

@Composable
private fun ThemeGrid(selected: KeyboardTheme, onSelect: (KeyboardTheme) -> Unit) {
    Column {
        KeyboardThemes.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { theme ->
                    ThemeSwatch(theme = theme, isSelected = theme.id == selected.id, onClick = { onSelect(theme) })
                }
            }
        }
    }
}

@Composable
private fun ThemeSwatch(theme: KeyboardTheme, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(theme.background)
                .padding(3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(if (isSelected) 44.dp else 40.dp)
                    .clip(CircleShape)
                    .background(theme.accent),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = theme.label,
            color = if (isSelected) theme.accent else Color(0xFFA0A0A0),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun <T> ChipRow(options: List<T>, labelOf: (T) -> String, selected: T, onSelect: (T) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Color(0xFF4C8BF5) else Color(0xFF2A2A2A))
                    .clickable { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(text = labelOf(option), color = Color.White)
            }
        }
    }
}
