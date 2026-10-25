package app.bitmessenger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.bitmessenger.bitter.BitterBrain
import app.bitmessenger.bitter.BitterMsg
import app.bitmessenger.bitter.BitterStore
import app.bitmessenger.data.Settings
import kotlinx.coroutines.launch

private val STARTERS = listOf("What can you do?", "Tell me a joke", "Help me write a text", "Plan my day")

/** Chatting with Bitter: his sprite talks while he answers, thinks while he waits. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BitterScreen(onBack: () -> Unit, openSettings: () -> Unit) {
    val p = LocalPalette.current
    val s = LocalBitSettings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val history by BitterStore.msgs.collectAsState()
    var draft by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    var streaming by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()
    val mood = when {
        streaming != null -> BitterMood.TALKING
        thinking -> BitterMood.THINKING
        else -> BitterMood.IDLE
    }
    LaunchedEffect(history.size, streaming, thinking) { list.scrollToItem(maxOf(0, list.layoutInfo.totalItemsCount - 1)) }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || thinking || streaming != null) return
        draft = ""
        BitterStore.add(BitterMsg(t, fromMe = true, at = System.currentTimeMillis()))
        thinking = true
        note = null
        scope.launch {
            val reply = BitterBrain.reply(context, Settings.now, BitterStore.msgs.value) { soFar -> thinking = false; streaming = soFar }
            BitterStore.add(BitterMsg(reply.text, fromMe = false, at = System.currentTimeMillis(), brain = reply.brain.label))
            streaming = null
            thinking = false
            note = reply.note
        }
    }

    Column(Modifier.fillMaxSize().background(s.wallpaper?.let { androidx.compose.ui.graphics.Color(it) } ?: p.bg).imePadding()) {
        TopBar(s.bitterName.uppercase(), onBack) {
            IconButton(onClick = openSettings) { Icon(Icons.Filled.Settings, "Bitter settings", tint = p.text) }
        }
        // Big Bitter on his stage: talks while replying, thinks while waiting.
        Column(Modifier.fillMaxWidth().background(p.surface).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BitterAvatar(avatarSize(96.dp), mood)
            Text(
                when (mood) { BitterMood.TALKING -> "TALKING…"; BitterMood.THINKING -> "THINKING…"; else -> "ASK ME ANYTHING" },
                style = px(8, p.bitter, glow = p.bitter), modifier = Modifier.padding(top = 10.dp),
            )
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(12.dp)) {
            if (history.isEmpty()) item {
                BitterBubble("Boo! 👻 I'm ${s.bitterName}, your pixel pal. Ask me anything, or tap an idea below.", null)
                FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    STARTERS.forEach { st -> PixelChip(st, false) { send(st) } }
                }
            }
            items(history, key = { it.at.toString() + it.fromMe }) { m ->
                if (m.fromMe) MyBubble(m.text) else BitterBubble(m.text, m.brain)
            }
            streaming?.let { t -> item(key = "streaming") { BitterBubble(t + "▌", null) } }
            if (thinking) item(key = "thinking") { TypingDots() }
            note?.let { n -> item(key = "note") { Text("ⓘ $n", style = px(7, p.dim), modifier = Modifier.padding(8.dp).clickable(onClick = openSettings)) } }
            if (history.isNotEmpty()) item(key = "clear") {
                Text("✕ CLEAR CHAT", style = px(7, p.dim), modifier = Modifier.padding(top = 16.dp).clickable { BitterStore.clear(); note = null }.padding(8.dp))
            }
        }
        Composer(draft, { draft = it }, placeholder = "MESSAGE ${s.bitterName.uppercase()}") { send(draft) }
    }
}

@Composable
private fun MyBubble(text: String) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.End) {
        Text(text, style = px(big(8), onColor(p.sent)), modifier = Modifier.widthIn(max = 300.dp).clip(bitShape(12.dp)).background(p.sent).padding(12.dp))
    }
}

@Composable
private fun BitterBubble(text: String, brain: String?) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Bottom) {
        BitterAvatar(36.dp, animate = false)
        Column(Modifier.padding(start = 8.dp)) {
            Text(
                text, style = px(big(8), onColor(p.received)),
                modifier = Modifier.widthIn(max = 280.dp).clip(bitShape(12.dp)).background(p.received).border(2.dp, p.bitter.copy(alpha = 0.5f), bitShape(12.dp)).padding(12.dp),
            )
            brain?.let { Text(it.uppercase(), style = px(6, p.dim), modifier = Modifier.padding(top = 4.dp, start = 4.dp)) }
        }
    }
}

@Composable
private fun TypingDots() {
    val p = LocalPalette.current
    var n by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(300); n = (n + 1) % 4 } }
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        BitterAvatar(36.dp, BitterMood.THINKING)
        Text("●".repeat(n).padEnd(3, '○'), style = px(10, p.bitter), modifier = Modifier.padding(start = 10.dp).clip(bitShape(10.dp)).background(p.received).padding(12.dp))
    }
}
