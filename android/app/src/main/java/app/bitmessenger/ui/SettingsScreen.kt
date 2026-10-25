package app.bitmessenger.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.bitmessenger.bitter.BitterBrain
import app.bitmessenger.bitter.BitterMsg
import app.bitmessenger.data.BitSettings
import app.bitmessenger.data.Settings
import app.bitmessenger.data.ThemeCode
import app.bitmessenger.sms.SmsRepo
import kotlinx.coroutines.launch

enum class Section(val title: String, val icon: String) {
    TEXTING("Texting app", "💬"),
    BITTER("Bitter", "👻"),
    THEME("Theme, colors & look", "🎨"),
    PAIRING("Device pairing", "🔗"),
    ABOUT("About", "ⓘ"),
}

@Composable
fun SettingsScreen(activity: TextingHost, initial: Section?, onBack: () -> Unit) {
    val p = LocalPalette.current
    var open by remember { mutableStateOf(initial) }
    BackHandler(open != null && initial == null) { open = null }
    Column(Modifier.fillMaxSize().background(p.bg)) {
        TopBar(open?.title?.uppercase() ?: "SETTINGS", onBack = { if (open != null && initial == null) open = null else onBack() })
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            val sec = open
            if (sec == null) {
                item { androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp)) }
                item { MenuGroup(Section.entries.map { s -> (s.icon to s.title) to { open = s } }) }
                item { Text("bitmessenger · 128bit family", style = px(7, p.dim), modifier = Modifier.padding(vertical = 24.dp)) }
            } else item {
                Card(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp)) {
                    when (sec) {
                        Section.TEXTING -> TextingSettings(activity)
                        Section.BITTER -> BitterSettings()
                        Section.THEME -> ThemeEditor()
                        Section.PAIRING -> PairingSettings()
                        Section.ABOUT -> AboutSettings()
                    }
                }
            }
        }
    }
}

@Composable
private fun TextingSettings(activity: TextingHost) {
    val p = LocalPalette.current
    val s = LocalBitSettings.current
    val isDefault by activity.defaultApp.collectAsState()
    Text(if (isDefault) "✓ BITMESSENGER IS YOUR TEXTING APP" else "BITMESSENGER IS A COMPANION RIGHT NOW", style = px(8, if (isDefault) p.good else p.accent))
    Info(
        if (isDefault) "New texts arrive here, with read and delivery receipts. Picture and group (MMS) messages can't be downloaded yet: you'll get a notice when one arrives. To get those, make your old texting app the default again."
        else "You can read and send texts now. Make bitmessenger your texting app so new texts arrive here and you can mark them read and delete them.\n\nHeads up: picture and group (MMS) messages and RCS chats can't be received by bitmessenger yet.",
    )
    if (!isDefault) Row { PixelChip("▶ MAKE IT MY TEXTING APP", true) { activity.requestDefaultSms() } }
    Toggle("Message sounds", "Play a chip blip when you send", s.sounds) { b, v -> b.copy(sounds = v) }
    Toggle("Vibration", "A tiny buzz when you send", s.haptics) { b, v -> b.copy(haptics = v) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BitterSettings() {
    val p = LocalPalette.current
    val s = LocalBitSettings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf(s.claudeKey) }
    var test by remember { mutableStateOf<String?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        BitterAvatar(64.dp)
        Column(Modifier.padding(start = 12.dp)) {
            Text(s.bitterName.uppercase(), style = px(11, p.bitter, glow = p.bitter))
            Text("Your pixel pal, pinned at the top of your chats.", style = px(7, p.dim), modifier = Modifier.padding(top = 6.dp))
        }
    }
    Text("NAME", style = px(7, p.dim), modifier = Modifier.padding(top = 14.dp))
    OutlinedTextField(s.bitterName, { v -> Settings.update { it.copy(bitterName = v.take(20).ifBlank { "Bitter" }) } }, Modifier.fillMaxWidth(), singleLine = true, textStyle = px(10, p.text))
    Text("BRAIN", style = px(8, p.accent), modifier = Modifier.padding(top = 14.dp))
    FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("AUTO" to "AUTO", "FAMILY" to "ON-DEVICE", "CLAUDE" to "CLAUDE", "OFFLINE" to "OFF").forEach { (id, label) ->
            PixelChip(label, s.bitterBrain == id) { Settings.update { it.copy(bitterBrain = id) } }
        }
    }
    Info(when (s.bitterBrain) {
        "AUTO" -> "Tries Bitcall's on-device AI first (private, free), then Claude if you've added a key."
        "FAMILY" -> "Only Bitcall's on-device AI. Turn it on in Bitcall → Settings → On-device AI. Nothing leaves your phone."
        "CLAUDE" -> "Only Claude, using your own API key. Your chat with Bitter is sent to Anthropic to answer."
        else -> "Bitter stays quiet."
    })
    Text("CLAUDE API KEY", style = px(8, p.accent), modifier = Modifier.padding(top = 10.dp))
    Info("Optional. Get one at console.anthropic.com. It's stored only on this phone and used only for Bitter's replies.")
    OutlinedTextField(
        key, { key = it.trim() }, Modifier.fillMaxWidth(), singleLine = true, textStyle = px(9, p.text),
        visualTransformation = PasswordVisualTransformation(), placeholder = { Text("sk-ant-…", style = px(8, p.dim)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PixelChip("SAVE KEY", key != s.claudeKey) { Settings.update { it.copy(claudeKey = key) } }
        if (s.claudeKey.isNotBlank()) PixelChip("REMOVE", false) { key = ""; Settings.update { it.copy(claudeKey = "") } }
        PixelChip("▶ TEST", false) {
            test = "Asking ${s.bitterName}…"
            scope.launch {
                val r = BitterBrain.reply(context, Settings.now, listOf(BitterMsg("Say hi in five words.", true, System.currentTimeMillis()))) {}
                test = "${r.brain.label.uppercase()}: ${r.text}" + (r.note?.let { "\n($it)" } ?: "")
            }
        }
    }
    test?.let { Text(it, style = px(7, p.good), modifier = Modifier.padding(top = 8.dp)) }
}

@Composable
private fun PairingSettings() {
    val p = LocalPalette.current
    val context = LocalContext.current
    val hasBitcall = remember { runCatching { context.packageManager.getPackageInfo("app.bitcall", 0); true }.getOrDefault(false) }
    Text(if (hasBitcall) "✓ BITCALL FOUND" else "BITCALL NOT INSTALLED", style = px(8, if (hasBitcall) p.good else p.dim))
    Info(
        "bitmessenger and Bitcall work as a pair:\n\n" +
            "• 📞 in a chat opens Bitcall with the number ready (when Bitcall is your phone app).\n" +
            "• 💬 TEXT in Bitcall opens the chat here (when bitmessenger is your texting app).\n" +
            "• Bitter can borrow Bitcall's on-device AI, if both apps are signed with the 128bit family key.\n" +
            "• Theme codes work in both: copy one in either app and paste it in the other.",
    )
    if (!hasBitcall) Row { PixelChip("GET BITCALL", true) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hootywhooo86/bitcall/releases")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    } }
}

@Composable
private fun AboutSettings() {
    val p = LocalPalette.current
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?" }
    Row(verticalAlignment = Alignment.CenterVertically) {
        MessengerLogo(48.dp)
        Column(Modifier.padding(start = 12.dp)) {
            Text("BITMESSENGER $version", style = px(10, p.text))
            Text("Talk, but chunkier. Part of the 128bit family.", style = px(7, p.dim), modifier = Modifier.padding(top = 6.dp))
        }
    }
    Info("Your texts stay in your phone's own message store. Bitter's chat is kept only on this phone.")
}

/** A tiny chat that redraws as you edit, so every change shows right away. */
@Composable
private fun ThemePreview() {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().clip(bitShape(10.dp)).background(LocalBitSettings.current.wallpaper?.let { Color(it) } ?: p.bg).border(3.dp, p.border, bitShape(10.dp)).padding(12.dp)) {
        Row(Modifier.fillMaxWidth().background(p.header), verticalAlignment = Alignment.CenterVertically) {
            MessengerLogo(28.dp)
            Text(" BITMESSENGER", style = px(8, p.text), modifier = Modifier.weight(1f))
            Text("PREVIEW", style = px(6, p.dim))
        }
        Row(
            Modifier.padding(top = 10.dp).fillMaxWidth().clip(bitShape(10.dp)).background(p.surface).border(3.dp, p.border, bitShape(10.dp)).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BitterAvatar(avatarSize(36.dp))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(LocalBitSettings.current.bitterName, style = px(9, p.text))
                Text("Ask me anything!", style = px(7, p.dim), modifier = Modifier.padding(top = 4.dp))
            }
            Box(Modifier.clip(bitShape(4.dp)).background(p.badge).padding(horizontal = 6.dp, vertical = 4.dp)) { Text("2", style = px(7, onColor(p.badge))) }
        }
        Text("Hey! Pizza tonight?", style = px(8, onColor(p.received)), modifier = Modifier.padding(top = 10.dp).clip(bitShape(10.dp)).background(p.received).padding(10.dp))
        Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.CenterEnd) {
            Text("YES 🍕", style = px(8, onColor(p.sent)), modifier = Modifier.clip(bitShape(10.dp)).background(p.sent).padding(10.dp))
        }
        Row(Modifier.padding(top = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text("💬 START CHAT", style = px(7, onColor(p.fab)), modifier = Modifier.clip(bitShape(8.dp)).background(p.fab).padding(10.dp))
        }
        Text("🛡 1 chat in Spam", style = px(7, onColor(p.banner)), modifier = Modifier.padding(top = 10.dp).fillMaxWidth().clip(bitShape(8.dp)).background(p.banner).padding(10.dp))
    }
}

@Composable
private fun Toggle(label: String, sub: String?, on: Boolean, set: (BitSettings, Boolean) -> BitSettings) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = px(9, p.text))
            sub?.let { Text(it, style = px(7, p.dim), modifier = Modifier.padding(top = 6.dp)) }
        }
        Switch(on, { v -> Settings.update { set(it, v) } }, colors = SwitchDefaults.colors(checkedTrackColor = p.accent))
    }
}

@Composable
private fun Info(text: String) {
    val p = LocalPalette.current
    Text(text, style = px(7, p.dim), modifier = Modifier.padding(vertical = 8.dp))
}

/**
 * The whole look in one place: palette presets, every themed colour (with a live preview),
 * corners / card size / pixel detail / text size, wallpaper, and a theme code shared with bitcall.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeEditor() {
    val p = LocalPalette.current
    val s = LocalBitSettings.current
    val clipboard = LocalClipboardManager.current
    var editing by remember { mutableStateOf<ColorSlot?>(null) }
    var shareNote by remember { mutableStateOf<String?>(null) }

    ThemePreview()

    Text("PALETTE", style = px(8, p.accent), modifier = Modifier.padding(top = 14.dp))
    FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PALETTES.forEach { pal -> PixelChip(pal.label, pal.id == s.palette) { Settings.update { it.copy(palette = pal.id) } } }
    }
    Info("Your color changes below sit on top of the palette, so you can start from any preset.")

    Text("COLORS — tap one to change it", style = px(8, p.accent), modifier = Modifier.padding(top = 12.dp))
    ColorSlot.entries.forEach { slot ->
        val c = slot.get(p)
        val custom = slot.name in s.colorOverrides
        Row(
            Modifier.fillMaxWidth().clip(bitShape(6.dp)).background(if (editing == slot) p.surface2 else Color.Transparent)
                .clickable { editing = if (editing == slot) null else slot }.padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(28.dp).clip(bitShape(4.dp)).background(c).border(2.dp, p.text.copy(alpha = 0.4f), bitShape(4.dp)))
            Text(slot.label, style = px(9, p.text), modifier = Modifier.weight(1f).padding(start = 12.dp))
            if (custom) Text("✎", style = px(10, p.accent))
        }
        if (editing == slot) {
            ColorPicker(
                c,
                onPick = { col -> Settings.update { it.copy(colorOverrides = it.colorOverrides + (slot.name to colorLong(col))) } },
                onReset = if (custom) ({ Settings.update { it.copy(colorOverrides = it.colorOverrides - slot.name) } }) else null,
            )
        }
    }
    if (s.colorOverrides.isNotEmpty()) Row(Modifier.padding(top = 8.dp)) {
        PixelChip("↺ RESET ALL COLORS", false) { Settings.update { it.copy(colorOverrides = emptyMap()) }; editing = null }
    }

    Text("LOOK", style = px(8, p.accent), modifier = Modifier.padding(top = 18.dp))
    ChoiceRow("CORNERS", listOf("PIXEL" to "▟ PIXEL", "ROUND" to "◖ ROUND", "SQUARE" to "■ SQUARE"), s.corners) { v -> Settings.update { it.copy(corners = v) } }
    ChoiceRow("CARD SIZE", listOf("COMPACT" to "S", "NORMAL" to "M", "BIG" to "L", "HUGE" to "XL"), s.cardSize) { v -> Settings.update { it.copy(cardSize = v) } }
    ChoiceRow("PHOTO PIXELS", listOf(16, 24, 32, 48, 64).map { it.toString() to "${it}px" }, s.pixelDetail.toString()) { v -> Settings.update { it.copy(pixelDetail = v.toInt()) } }
    ChoiceRow("TEXT SIZE", listOf(0.85f to "S", 1f to "M", 1.15f to "L", 1.3f to "XL").map { it.first.toString() to it.second }, s.textScale.toString()) { v ->
        Settings.update { it.copy(textScale = v.toFloat()) }
    }
    Toggle("CRT glow", "Scanlines and phosphor glow (dark palettes)", s.crt) { b, v -> b.copy(crt = v) }
    Toggle("Vibration", "A tiny buzz when you send", s.haptics) { b, v -> b.copy(haptics = v) }

    Text("WALLPAPER", style = px(8, p.accent), modifier = Modifier.padding(top = 18.dp))
    Info("For every chat. Each chat can have its own too: tap 🎨 at the top of a chat.")
    WallpaperPicker(null)

    Text("SHARE", style = px(8, p.accent), modifier = Modifier.padding(top = 18.dp))
    Info("A theme code holds your palette, colors and look. It's the same code Bitcall uses: copy it in one app, paste it in the other to match them.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PixelChip("📋 COPY CODE", false) { clipboard.setText(AnnotatedString(ThemeCode.encode(s))); shareNote = "✓ Theme code copied." }
        PixelChip("📥 PASTE CODE", false) {
            val code = clipboard.getText()?.text.orEmpty()
            val next = ThemeCode.decode(code, Settings.now)
            if (next != null) { Settings.update { next }; shareNote = "✓ Theme loaded." } else shareNote = "That isn't a 128bit theme code."
        }
    }
    shareNote?.let { Text(it, style = px(7, p.good), modifier = Modifier.padding(top = 8.dp)) }
    Row(Modifier.padding(top = 14.dp)) {
        PixelChip("↺ RESET WHOLE LOOK", false) {
            val d = BitSettings()
            Settings.update {
                it.copy(palette = d.palette, colorOverrides = d.colorOverrides, keyColors = d.keyColors, corners = d.corners, cardSize = d.cardSize,
                    pixelDetail = d.pixelDetail, textScale = d.textScale, crt = d.crt, keyLetters = d.keyLetters, haptics = d.haptics,
                    wallpaper = null, chatWallpapers = emptyMap())
            }
            editing = null
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(label: String, options: List<Pair<String, String>>, current: String, onPick: (String) -> Unit) {
    val p = LocalPalette.current
    Text(label, style = px(7, p.dim), modifier = Modifier.padding(top = 10.dp))
    FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (value, text) -> PixelChip(text, value == current) { onPick(value) } }
    }
}

/** Quick swatches, hue / saturation / brightness sliders, and a hex box: any colour at all. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPicker(color: Color, onPick: (Color) -> Unit, onReset: (() -> Unit)?) {
    val p = LocalPalette.current
    val hsv = remember(color) { FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) } }
    var hex by remember(color) { mutableStateOf("%06X".format(color.toArgb() and 0xFFFFFF)) }
    fun fromHsv(h: Float, sat: Float, v: Float) = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, sat, v)))
    val sliderColors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.surface2)

    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(bitShape(8.dp)).background(p.bg).padding(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (SWATCHES.map { Color(it) } + p.keys).distinct().forEach { sw ->
                Box(
                    Modifier.size(30.dp).clip(bitShape(4.dp)).background(sw)
                        .border(2.dp, if (sw == color) p.text else p.surface2, bitShape(4.dp)).clickable { onPick(sw) },
                )
            }
        }
        Text("HUE", style = px(7, p.dim), modifier = Modifier.padding(top = 10.dp))
        Slider(hsv[0], { onPick(fromHsv(it, hsv[1], hsv[2])) }, valueRange = 0f..360f, colors = sliderColors)
        Text("COLOR", style = px(7, p.dim))
        Slider(hsv[1], { onPick(fromHsv(hsv[0], it, hsv[2])) }, colors = sliderColors)
        Text("BRIGHTNESS", style = px(7, p.dim))
        Slider(hsv[2], { onPick(fromHsv(hsv[0], hsv[1], it)) }, colors = sliderColors)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(bitShape(6.dp)).background(color))
            OutlinedTextField(
                hex,
                { v ->
                    hex = v.filter { it.isLetterOrDigit() }.take(6).uppercase()
                    hex.toLongOrNull(16)?.takeIf { hex.length == 6 }?.let { onPick(Color(0xFF000000 or it)) }
                },
                Modifier.weight(1f).padding(start = 10.dp), singleLine = true, textStyle = px(10, p.text),
                prefix = { Text("#", style = px(10, p.dim)) },
            )
        }
        onReset?.let { Row(Modifier.padding(top = 8.dp)) { PixelChip("↺ USE PALETTE COLOR", false, it) } }
    }
}

private fun colorLong(c: Color): Long =
    (((c.alpha * 255).toLong() shl 24) or ((c.red * 255).toLong() shl 16) or ((c.green * 255).toLong() shl 8) or (c.blue * 255).toLong())
