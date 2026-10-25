package app.bitmessenger.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.bitmessenger.bitter.BitterStore
import app.bitmessenger.data.Settings
import app.bitmessenger.sms.Conversation
import app.bitmessenger.sms.SmsRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date

/** "8 min", "15:37", "Mon", "Oct 3": the same short times Google Messages shows. */
fun shortTime(context: Context, ms: Long, now: Long = System.currentTimeMillis()): String {
    val age = now - ms
    if (age < DateUtils.MINUTE_IN_MILLIS) return "now"
    if (age < DateUtils.HOUR_IN_MILLIS) return "${age / DateUtils.MINUTE_IN_MILLIS} min"
    if (DateUtils.isToday(ms)) return DateFormat.getTimeFormat(context).format(Date(ms))
    if (age < 6 * DateUtils.DAY_IN_MILLIS) return DateFormat.format("EEE", ms).toString()
    val sameYear = Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)
    return DateFormat.format(if (sameYear) "MMM d" else "MMM d, yyyy", ms).toString()
}

private const val SPAM_BANNER_QUIET = 14 * DateUtils.DAY_IN_MILLIS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(activity: TextingHost, conversations: List<Conversation>, open: (Screen) -> Unit, refresh: () -> Unit) {
    val p = LocalPalette.current
    val s = LocalBitSettings.current
    val context = LocalContext.current
    val isDefault by activity.defaultApp.collectAsState()
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var bodyHits by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var profile by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<Conversation?>(null) }
    LaunchedEffect(query) { bodyHits = withContext(Dispatchers.IO) { SmsRepo.search(context, query) } }

    val hidden = s.archived + s.trashed + s.spam
    val shown = remember(conversations, s.pinned, hidden, query, bodyHits) {
        conversations.filter { it.threadId !in hidden }
            .filter { query.isBlank() || it.title.contains(query, true) || it.snippet.contains(query, true) || it.threadId in bodyHits }
            .sortedWith(compareByDescending<Conversation> { it.threadId in s.pinned }.thenByDescending { it.date })
    }
    val spamCount = conversations.count { it.threadId in s.spam }

    Box(Modifier.fillMaxSize().background(p.bg)) {
        Column(Modifier.fillMaxSize()) {
            // Header: logo + name, search, profile avatar (Google Messages layout, pixel skin).
            Row(Modifier.fillMaxWidth().background(p.header).padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (searching) {
                    OutlinedTextField(
                        query, { query = it }, Modifier.weight(1f), singleLine = true, textStyle = px(10, p.text),
                        placeholder = { Text("SEARCH MESSAGES", style = px(8, p.dim)) },
                    )
                    IconButton(onClick = { searching = false; query = "" }) { Icon(Icons.Filled.Close, "Close search", tint = p.text) }
                } else {
                    MessengerLogo(34.dp)
                    Text("BIT", style = px(13, p.logo, glow = p.logo), modifier = Modifier.padding(start = 8.dp))
                    Text("MESSENGER", style = px(13, p.text), modifier = Modifier.weight(1f), maxLines = 1)
                    IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, "Search", tint = p.text, modifier = Modifier.size(28.dp)) }
                    Box(Modifier.clip(bitShape(8.dp)).clickable { profile = true }.padding(4.dp)) { MyAvatar(40.dp) }
                }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 160.dp)) {
                if (!SmsRepo.canRead(context)) item {
                    Banner("🔒 bitmessenger needs permission to read your texts.", "ALLOW") { activity.askMissingPermissions() }
                } else if (!isDefault) item {
                    Banner("▶ Make bitmessenger your texting app to send receipts, mark read and delete. Picture/group (MMS) messages can't be received yet.", "SET UP") { activity.requestDefaultSms() }
                }
                if (spamCount > 0 && System.currentTimeMillis() - s.spamBannerHiddenAt > SPAM_BANNER_QUIET && query.isBlank()) item {
                    SpamBanner(spamCount, onDismiss = { Settings.update { it.copy(spamBannerHiddenAt = System.currentTimeMillis()) } }, onView = { open(Screen.Folder(Folder.SPAM)) })
                }
                // Bitter is always the first chat, pinned, and never archived.
                if (query.isBlank() || "bitter".contains(query, true) || s.bitterName.contains(query, true)) item { BitterRow { open(Screen.Bitter) } }
                items(shown, key = { it.threadId }) { c ->
                    ConversationRow(c, pinned = c.threadId in s.pinned, onClick = { open(Screen.Thread(c.threadId, c.people.map { it.address })) }, onLongClick = { actionsFor = c })
                }
                if (shown.isEmpty() && SmsRepo.canRead(context)) item {
                    Text(if (query.isBlank()) "No texts yet. Tap START CHAT to send one." else "Nothing matches \"$query\".", style = px(8, p.dim), modifier = Modifier.padding(20.dp))
                }
            }
        }
        // Bitter's button sits where Google's sparkle button is, above START CHAT.
        Column(Modifier.align(Alignment.BottomEnd).padding(16.dp), horizontalAlignment = Alignment.End) {
            PixelButton(p.fab, Modifier.size(64.dp), onClick = { open(Screen.Bitter) }) { BitterSprite(48.dp) }
            Spacer(Modifier.height(12.dp))
            PixelButton(p.fab, onClick = { open(Screen.NewChat) }) {
                Text("💬 START CHAT", style = px(11, onColor(p.fab)), modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp))
            }
        }
    }

    if (profile) ProfileSheet(onDismiss = { profile = false }, open = { profile = false; open(it) }, refresh = refresh)
    actionsFor?.let { c -> ConversationActions(c, onDismiss = { actionsFor = null }, refresh = refresh) }
}

@Composable
private fun Banner(text: String, action: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.padding(top = 8.dp).fillMaxWidth().clip(bitShape(10.dp)).background(p.banner).padding(14.dp)) {
        Text(text, style = px(8, onColor(p.banner)))
        Row(Modifier.padding(top = 10.dp)) { PixelChip(action, true, onClick) }
    }
}

@Composable
private fun SpamBanner(count: Int, onDismiss: () -> Unit, onView: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.padding(top = 8.dp).fillMaxWidth().clip(bitShape(14.dp)).background(p.banner).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🛡", style = px(16, onColor(p.banner)))
            Text("$count ${if (count == 1) "chat" else "chats"} in Spam & blocked", style = px(9, onColor(p.banner)), modifier = Modifier.padding(start = 12.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
            Text("DISMISS", style = px(9, onColor(p.banner)), modifier = Modifier.clickable(onClick = onDismiss).padding(10.dp))
            Spacer(Modifier.width(12.dp))
            Text("VIEW", style = px(9, onColor(p.banner)), modifier = Modifier.clickable(onClick = onView).padding(10.dp))
        }
    }
}

@Composable
private fun BitterRow(onClick: () -> Unit) {
    val p = LocalPalette.current
    val s = LocalBitSettings.current
    val msgs by BitterStore.msgs.collectAsState()
    val last = msgs.lastOrNull()
    val context = LocalContext.current
    RowShell(onClick = onClick, onLongClick = null, highlight = true) {
        BitterAvatar(avatarSize(52.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(s.bitterName, style = px(big(11), p.text), maxLines = 1)
            Text(
                last?.let { (if (it.fromMe) "You: " else "") + it.text } ?: "Your pixel pal. Ask me anything! 👻",
                style = px(8, p.dim), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(last?.let { shortTime(context, it.at) } ?: "", style = px(7, p.dim))
            Text("📌", style = px(10, p.dim), modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowShell(onClick: () -> Unit, onLongClick: (() -> Unit)?, highlight: Boolean = false, content: @Composable RowScope.() -> Unit) {
    val p = LocalPalette.current
    val shape = bitShape(12.dp)
    Row(
        Modifier.padding(top = 8.dp).fillMaxWidth().clip(shape).background(p.surface)
            .border(3.dp, if (highlight) p.bitter.copy(alpha = 0.7f) else p.border, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
fun ConversationRow(c: Conversation, pinned: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val unread = c.unread > 0
    RowShell(onClick, onLongClick) {
        GroupAvatar(c, avatarSize(52.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(c.title, style = px(big(11), if (unread) p.text else p.text.copy(alpha = 0.85f), glow = if (unread) p.text.copy(alpha = 0.35f) else null),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (if (c.fromMe) "✓✓ You: " else "") + c.snippet.replace('\n', ' '),
                style = px(8, if (unread) p.text else p.dim), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
            )
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
            Text(shortTime(context, c.date), style = px(7, if (unread) p.text else p.dim))
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (pinned) Text("📌", style = px(9, p.dim))
                if (unread) Box(
                    Modifier.padding(start = 6.dp).clip(bitShape(4.dp)).background(p.badge).padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(if (c.unread > 99) "99+" else "${c.unread}", style = px(8, onColor(p.badge))) }
            }
        }
    }
}

/** One person: their pixel photo. A group: up to three small faces stacked like Google's. */
@Composable
fun GroupAvatar(c: Conversation, size: androidx.compose.ui.unit.Dp) {
    if (c.people.size <= 1) {
        val person = c.people.firstOrNull()
        PixelAvatar(person?.label ?: "?", person?.photoUri, size)
        return
    }
    Box(Modifier.size(size)) {
        val small = size * 0.6f
        c.people.take(3).forEachIndexed { i, person ->
            val align = listOf(Alignment.TopCenter, Alignment.BottomStart, Alignment.BottomEnd)[i]
            Box(Modifier.align(align)) { PixelAvatar(person.label, person.photoUri, small) }
        }
    }
}

/** The phone owner's own contact card ("Me"), as the header avatar. */
@Composable
fun MyAvatar(size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    var me by remember { mutableStateOf<Pair<String, String?>?>(null) }
    LaunchedEffect(Unit) {
        me = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.query(ContactsContract.Profile.CONTENT_URI, arrayOf(ContactsContract.Profile.DISPLAY_NAME, ContactsContract.Profile.PHOTO_URI), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) (c.getString(0) ?: "Me") to c.getString(1) else null }
            }.getOrNull()
        }
    }
    PixelAvatar(me?.first ?: "Me", me?.second, size)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ConversationActions(c: Conversation, onDismiss: () -> Unit, refresh: () -> Unit) {
    val p = LocalPalette.current
    val s = LocalBitSettings.current
    val context = LocalContext.current
    val id = c.threadId
    fun done() { onDismiss(); refresh() }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.bg, contentColor = p.text) {
        Text(c.title, style = px(11, p.text), modifier = Modifier.padding(horizontal = 20.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        FlowRow(Modifier.padding(20.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelChip(if (id in s.pinned) "📌 UNPIN" else "📌 PIN", false) { Settings.update { it.copy(pinned = it.pinned.toggle(id)) }; done() }
            PixelChip("🗄 ARCHIVE", false) { Settings.update { it.copy(archived = it.archived + id, pinned = it.pinned - id) }; done() }
            if (c.unread > 0) PixelChip("✓ MARK READ", false) { SmsRepo.markRead(context, id); done() }
            c.people.firstOrNull()?.let { person ->
                PixelChip("📞 CALL", false) { dial(context, person.address); onDismiss() }
            }
            PixelChip("🛡 SPAM", false) { Settings.update { it.copy(spam = it.spam + id, pinned = it.pinned - id) }; done() }
            PixelChip("🗑 TRASH", false) { Settings.update { it.copy(trashed = it.trashed + id, pinned = it.pinned - id) }; done() }
        }
        Spacer(Modifier.height(12.dp))
    }
}

fun Set<Long>.toggle(id: Long) = if (id in this) this - id else this + id

/** Calls go to the phone app (bitcall, when it's the default dialer) with the number ready. */
fun dial(context: Context, number: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

enum class Folder(val title: String, val icon: String) {
    ARCHIVED("Archived", "🗄"),
    TRASH("Trash", "🗑"),
    SPAM("Spam & blocked", "🛡"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSheet(onDismiss: () -> Unit, open: (Screen) -> Unit, refresh: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.bg, contentColor = p.text) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().clip(bitShape(14.dp)).background(p.surface).border(3.dp, p.border, bitShape(14.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MyAvatar(64.dp)
                Column(Modifier.padding(start = 14.dp)) {
                    Text("YOUR PROFILE", style = px(10, p.text))
                    Text("128bit family", style = px(7, p.dim), modifier = Modifier.padding(top = 6.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            MenuGroup(
                listOf(
                    "🗄" to "Archived" to { open(Screen.Folder(Folder.ARCHIVED)) },
                    "🗑" to "Trash" to { open(Screen.Folder(Folder.TRASH)) },
                    "🛡" to "Spam & blocked" to { open(Screen.Folder(Folder.SPAM)) },
                    "✓" to "Mark all as read" to { SmsRepo.markAllRead(context); onDismiss(); refresh() },
                    "🔗" to "Device pairing" to { open(Screen.Settings(Section.PAIRING)) },
                ),
            )
            Spacer(Modifier.height(12.dp))
            MenuGroup(
                listOf(
                    "⚙" to "Messages settings" to { open(Screen.Settings()) },
                    "🎨" to "Theme, colors & look" to { open(Screen.Settings(Section.THEME)) },
                    "?" to "Help & feedback" to {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hootywhooo86/bitmessenger/issues")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                ),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun MenuGroup(rows: List<Pair<Pair<String, String>, () -> Unit>>) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().clip(bitShape(14.dp)).background(p.surface).border(3.dp, p.border, bitShape(14.dp))) {
        rows.forEachIndexed { i, (label, action) ->
            Row(Modifier.fillMaxWidth().clickable(onClick = action).padding(horizontal = 18.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label.first, style = px(12, p.accent), modifier = Modifier.width(36.dp))
                Text(label.second, style = px(10, p.text))
            }
            if (i < rows.lastIndex) Box(Modifier.fillMaxWidth().height(2.dp).background(p.bg))
        }
    }
}

/** Archived / Trash / Spam lists, each with a way back out. */
@Composable
fun FolderScreen(folder: Folder, conversations: List<Conversation>, onBack: () -> Unit, open: (Screen) -> Unit, refresh: () -> Unit) {
    val p = LocalPalette.current
    val s = LocalBitSettings.current
    val context = LocalContext.current
    val ids = when (folder) { Folder.ARCHIVED -> s.archived; Folder.TRASH -> s.trashed; Folder.SPAM -> s.spam }
    val shown = conversations.filter { it.threadId in ids }
    Column(Modifier.fillMaxSize().background(p.bg)) {
        TopBar("${folder.icon} ${folder.title.uppercase()}", onBack)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
            item {
                Text(
                    when (folder) {
                        Folder.ARCHIVED -> "Archived chats stay out of your list. New texts still arrive here."
                        Folder.TRASH -> if (SmsRepo.isDefault(context)) "Hidden chats. DELETE removes one for good." else "Hidden chats. Deleting for good needs bitmessenger to be your texting app."
                        Folder.SPAM -> "Chats you marked as spam: no notifications, hidden from your list."
                    },
                    style = px(7, p.dim), modifier = Modifier.padding(4.dp),
                )
            }
            if (shown.isEmpty()) item { Text("Nothing here.", style = px(9, p.dim), modifier = Modifier.padding(16.dp)) }
            items(shown, key = { it.threadId }) { c ->
                ConversationRow(c, pinned = false, onClick = { open(Screen.Thread(c.threadId, c.people.map { it.address })) }, onLongClick = {})
                Row(Modifier.padding(top = 6.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelChip("↩ RESTORE", false) {
                        Settings.update { it.copy(archived = it.archived - c.threadId, trashed = it.trashed - c.threadId, spam = it.spam - c.threadId) }
                    }
                    if (folder == Folder.TRASH && SmsRepo.isDefault(context)) PixelChip("✕ DELETE", false) {
                        if (SmsRepo.deleteThread(context, c.threadId)) Settings.update { it.copy(trashed = it.trashed - c.threadId) }
                        refresh()
                    }
                }
            }
        }
    }
}

@Composable
fun TopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().background(p.header).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = p.text) }
        Text(title, style = px(11, p.text), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}
