package com.aikeyboard.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aikeyboard.assistant.ime.TypingHistoryStore

/** Lets the user see and clear the on-device typing history Type Assist has recorded. */
class TypingHistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = TypingHistoryStore(applicationContext)

        setContent {
            var days by remember { mutableStateOf(store.availableDays()) }
            var selectedDay by remember { mutableStateOf<String?>(null) }

            MaterialTheme {
                Surface(color = Color(0xFF141414)) {
                    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                        Text(
                            text = "Typing History",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Stored only on this device, encrypted at rest. " +
                                "Password fields are never recorded.",
                            color = Color(0xFFA0A0A0),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(20.dp))

                        val day = selectedDay
                        if (day == null) {
                            if (days.isEmpty()) {
                                Text("No history yet.", color = Color(0xFFA0A0A0))
                            } else {
                                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                                    items(days) { entry ->
                                        Text(
                                            text = entry,
                                            color = Color.White,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedDay = entry }
                                                .padding(vertical = 14.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                Button(onClick = {
                                    store.clearAll()
                                    days = store.availableDays()
                                }) {
                                    Text("Clear all history")
                                }
                            }
                        } else {
                            Text(day, color = Color.White, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(10.dp))
                            Text(store.entriesFor(day), color = Color(0xFFCCCCCC))
                            Spacer(Modifier.height(20.dp))
                            Button(onClick = { selectedDay = null }) {
                                Text("Back")
                            }
                        }
                    }
                }
            }
        }
    }
}
