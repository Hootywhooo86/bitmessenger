package app.bitmessenger.ui

import android.Manifest
import android.content.pm.PackageManager
import android.provider.ContactsContract
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.bitmessenger.sms.Phones
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class Pick(val name: String, val number: String, val photo: String?)

/** Pick one or more people (or type a number), then START opens that chat. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewChatScreen(onBack: () -> Unit, onOpen: (List<String>) -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var all by remember { mutableStateOf<List<Pick>>(emptyList()) }
    var chosen by remember { mutableStateOf<List<Pick>>(emptyList()) }
    LaunchedEffect(Unit) {
        all = withContext(Dispatchers.IO) {
            if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return@withContext emptyList()
            val out = ArrayList<Pick>()
            runCatching {
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.PHOTO_URI),
                    null, null, "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE",
                )?.use { c -> while (c.moveToNext()) out += Pick(c.getString(0).orEmpty(), c.getString(1).orEmpty(), c.getString(2)) }
            }
            out.distinctBy { it.name + Phones.key(it.number) }
        }
    }
    val typedNumber = query.filter { it.isDigit() || it == '+' }.takeIf { it.length >= 3 && query.none { ch -> ch.isLetter() } }
    val shown = all.filter { query.isBlank() || it.name.contains(query, true) || Phones.key(it.number).contains(query.filter(Char::isDigit).ifEmpty { "\u0000" }) }

    Column(Modifier.fillMaxSize().background(p.bg)) {
        TopBar("NEW CHAT", onBack) {
            if (chosen.isNotEmpty()) PixelChip("START ▶", true) { onOpen(chosen.map { it.number }) }
        }
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth().padding(12.dp), singleLine = true, textStyle = px(10, p.text),
            placeholder = { Text("NAME OR NUMBER", style = px(8, p.dim)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
        if (chosen.isNotEmpty()) FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            chosen.forEach { c -> PixelChip("✕ ${c.name.ifBlank { Phones.pretty(c.number) }}", true) { chosen = chosen - c } }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
            typedNumber?.let { n ->
                item {
                    PickRow(Pick("", n, null), selected = false) { onOpen((chosen.map { it.number } + n).distinct()) }
                }
            }
            items(shown, key = { it.name + it.number }) { c ->
                PickRow(c, selected = c in chosen) { chosen = if (c in chosen) chosen - c else chosen + c; query = "" }
            }
            if (all.isEmpty()) item { Text("Type a number above to start a chat.", style = px(8, p.dim), modifier = Modifier.padding(16.dp)) }
        }
    }
}

@Composable
private fun PickRow(c: Pick, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = bitShape(12.dp)
    Row(
        Modifier.padding(vertical = 4.dp).fillMaxWidth().clip(shape).background(p.surface).border(3.dp, if (selected) p.accent else p.border, shape)
            .clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelAvatar(c.name.ifBlank { c.number }, c.photo, avatarSize(44.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(c.name.ifBlank { "Text ${Phones.pretty(c.number)}" }, style = px(10, p.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (c.name.isNotBlank()) Text(Phones.pretty(c.number), style = px(8, p.dim), modifier = Modifier.padding(top = 6.dp))
        }
        Text(if (selected) "✓" else "+", style = px(14, if (selected) p.accent else p.dim))
    }
}
