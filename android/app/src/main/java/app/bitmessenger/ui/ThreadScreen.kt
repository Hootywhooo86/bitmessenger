package app.bitmessenger.ui

import android.graphics.ImageDecoder
import android.net.Uri
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.bitmessenger.data.Settings
import app.bitmessenger.sms.Conversation
import app.bitmessenger.sms.Msg
import app.bitmessenger.sms.MsgState
import app.bitmessenger.sms.SmsRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

@Composable
fun ThreadScreen(screen: Screen.Thread, conversations: List<Conversation>, onBack: () -> Unit, refresh: () -> Unit) {
    val p = LocalPalette.current
    val s = LocalBitSettings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val convo = conversations.firstOrNull { it.threadId == screen.threadId }
    val people = convo?.people ?: screen.addresses.map { SmsRepo.person(context, it) }
    val addresses = people.map { it.address }.ifEmpty { screen.addresses }
    val title = people.joinToString(", ") { it.label }.ifBlank { "New message" }
    var msgs by remember { mutableStateOf<List<Msg>>(emptyList()) }
    var draft by remember(screen) { mutableStateOf(screen.draft) }
    var reload by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var paint by remember { mutableStateOf(false) }
    val list = rememberLazyListState()

    // Reload whenever the store changes (conversations list refreshes on every change).
    LaunchedEffect(screen.threadId, conversations, reload) {
        msgs = withContext(Dispatchers.IO) { if (screen.threadId >= 0) SmsRepo.messages(context, screen.threadId) else emptyList() }
        if (msgs.isNotEmpty()) list.scrollToItem(msgs.lastIndex)
        if (convo != null && convo.unread > 0) withContext(Dispatchers.IO) { SmsRepo.markRead(context, screen.threadId) }
    }
    val wallpaper = s.chatWallpapers[screen.threadId]?.let { Color(it) } ?: s.wallpaper?.let { Color(it) } ?: p.bg

    Column(Modifier.fillMaxSize().background(wallpaper).imePadding()) {
        TopBar(title, onBack) {
            IconButton(onClick = { paint = !paint }) { Icon(Icons.Filled.Palette, "Chat wallpaper", tint = p.text) }
            addresses.firstOrNull()?.let { n ->
                IconButton(onClick = { dial(context, n) }) { Icon(Icons.Filled.Call, "Call", tint = p.call, modifier = Modifier.size(28.dp)) }
            }
        }
        if (paint) WallpaperPicker(screen.threadId)
        if (convo?.group == true) Text(
            "Group texts send as a separate text to each person.", style = px(7, p.dim),
            modifier = Modifier.fillMaxWidth().background(p.surface).padding(10.dp),
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(12.dp)) {
            itemsIndexed(msgs, key = { _, m -> (if (m.mms) "m" else "s") + m.id }) { i, m ->
                val prev = msgs.getOrNull(i - 1)
                if (prev == null || m.date - prev.date > 30 * DateUtils.MINUTE_IN_MILLIS) DayStamp(m.date)
                val showSender = convo?.group == true && !m.fromMe && prev?.address != m.address
                Bubble(m, if (showSender) SmsRepo.person(context, m.address).label else null, last = i == msgs.lastIndex || msgs[i + 1].fromMe != m.fromMe)
            }
            if (msgs.isEmpty()) item {
                Text("Say hi 👋", style = px(9, p.dim), modifier = Modifier.fillMaxWidth().padding(24.dp))
            }
        }
        error?.let { Text(it, style = px(8, p.bad), modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) }
        Composer(draft, { draft = it }, enabled = addresses.isNotEmpty()) {
            val body = draft.trim()
            if (body.isEmpty()) return@Composer
            scope.launch {
                val ok = withContext(Dispatchers.IO) { SmsRepo.send(context, addresses, body) }
                if (ok) { draft = ""; error = null; reload++; refresh() } else error = "Couldn't send. Check the SMS permission and signal."
            }
        }
    }
}

@Composable
private fun DayStamp(ms: Long) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val time = DateFormat.getTimeFormat(context).format(Date(ms))
    val day = if (DateUtils.isToday(ms)) "Today" else DateFormat.format("EEE, MMM d", ms).toString()
    Text("$day · $time", style = px(7, p.dim), modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

@Composable
fun Bubble(m: Msg, sender: String?, last: Boolean) {
    val p = LocalPalette.current
    val bg = if (m.fromMe) p.sent else p.received
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalAlignment = if (m.fromMe) Alignment.End else Alignment.Start) {
        sender?.let { Text(it, style = px(7, p.dim), modifier = Modifier.padding(start = 6.dp, bottom = 4.dp)) }
        Column(Modifier.widthIn(max = 300.dp).clip(bitShape(12.dp)).background(bg).padding(horizontal = 12.dp, vertical = 10.dp)) {
            m.imageUri?.let { MmsImage(it) }
            if (m.body.isNotBlank()) Text(m.body, style = px(big(8), onColor(bg)))
            else if (m.imageUri == null && m.mms) Text("📷 Picture message", style = px(8, onColor(bg)))
        }
        if (m.fromMe && last) Text(
            when (m.state) {
                MsgState.SENDING -> "SENDING…"
                MsgState.SENT -> "✓ SENT"
                MsgState.DELIVERED -> "✓✓ DELIVERED"
                MsgState.FAILED -> "✕ NOT SENT"
                MsgState.RECEIVED -> ""
            },
            style = px(6, if (m.state == MsgState.FAILED) p.bad else p.dim), modifier = Modifier.padding(top = 4.dp, end = 4.dp),
        )
    }
}

@Composable
private fun MmsImage(uri: String) {
    val context = LocalContext.current
    var img by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        img = withContext(Dispatchers.IO) {
            runCatching { ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, Uri.parse(uri))) { d, _, _ -> d.setTargetSampleSize(2) }.asImageBitmap() }.getOrNull()
        }
    }
    img?.let { Image(it, "Picture", Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(bitShape(8.dp)).padding(bottom = 6.dp), contentScale = ContentScale.Fit) }
}

@Composable
fun Composer(text: String, onText: (String) -> Unit, enabled: Boolean = true, placeholder: String = "TEXT MESSAGE", onSend: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().background(p.header).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            text, onText, Modifier.weight(1f).heightIn(min = 56.dp), enabled = enabled, textStyle = px(10, p.text), maxLines = 5,
            placeholder = { Text(placeholder, style = px(8, p.dim)) },
        )
        Spacer(Modifier.size(10.dp))
        PixelButton(if (text.isBlank()) p.surface2 else p.accent, Modifier.size(width = 64.dp, height = 56.dp), onClick = onSend) {
            Text("▶", style = px(16, onColor(if (text.isBlank()) p.surface2 else p.accent)))
        }
    }
}

/** Per-chat wallpaper: a colour behind this chat only, or the app-wide one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WallpaperPicker(threadId: Long?) {
    val p = LocalPalette.current
    val s = LocalBitSettings.current
    val current = if (threadId == null) s.wallpaper else s.chatWallpapers[threadId]
    val choices = listOf(0xFF0D0D1A, 0xFF000000, 0xFF1A1A2E, 0xFF10241A, 0xFF241024, 0xFF0B1E33, 0xFF2A1A0A, 0xFF1E1E1E, 0xFFFFF4F8, 0xFFE8F5E9) + p.keys.take(6).map { colorArgb(darken(it, 0.25f)) }
    Column(Modifier.fillMaxWidth().background(p.surface).padding(12.dp)) {
        Text(if (threadId == null) "WALLPAPER FOR EVERY CHAT" else "WALLPAPER FOR THIS CHAT", style = px(7, p.dim))
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            choices.distinct().forEach { c ->
                Box(
                    Modifier.size(34.dp).clip(bitShape(4.dp)).background(Color(c)).border(2.dp, if (c == current) p.text else p.border, bitShape(4.dp))
                        .clickable {
                            Settings.update {
                                if (threadId == null) it.copy(wallpaper = c) else it.copy(chatWallpapers = it.chatWallpapers + (threadId to c))
                            }
                        },
                )
            }
            Text("↺", style = px(14, p.text), modifier = Modifier.clickable {
                Settings.update { if (threadId == null) it.copy(wallpaper = null) else it.copy(chatWallpapers = it.chatWallpapers - threadId) }
            }.padding(8.dp))
        }
    }
}

fun colorArgb(c: Color): Long =
    (((c.alpha * 255).toLong() shl 24) or ((c.red * 255).toLong() shl 16) or ((c.green * 255).toLong() shl 8) or (c.blue * 255).toLong())
