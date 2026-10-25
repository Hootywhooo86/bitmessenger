package app.bitmessenger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Bitter, the 128bit family's pixel ghost: 32x32 frames, mirror-symmetric silhouette, drawn cell by
 * cell with no smoothing. O outline, B body, S scanline, D shade, H highlight, W eye white,
 * P pupil, L pupil shine, M mouth, T tongue, C blush. Frames shift 1px for the float bob.
 * Generated from the sprite sheet script; edit the sheet, not these strings, to change him.
 */
val BITTER_FRAMES: Map<String, List<String>> = mapOf(
    "idle1" to listOf(
        "................................",
        "...........OOOOOOOOOO...........",
        ".........OOBBBBBBBBBDOO.........",
        "........OSSSSSSSSSSSSSDO........",
        ".......OBBBBBBBBBBBBBBBDO.......",
        "......OSHHHSSSSSSSSSSSSSDO......",
        ".....OBHHBBBBBBBBBBBBBBBBDO.....",
        "....OSSHSSSSSSSSSSSSSSSSSSDO....",
        "....OBHBBBBBBBBBBBBBBBBBBBDO....",
        "...OSSHSSSSSSSSSSSSSSSSSSSSDO...",
        "...OBBBBBWWWWBBBBBBWWWWBBBBDO...",
        "...OSSSSWWWWWWSSSSWWWWWWSSSDO...",
        "...OBBBBWWWWWWBBBBWWWWWWBBBDO...",
        "...OSSSSWWLPWWSSSSWWLPWWSSSDO...",
        "..OBBBBBWWPPWWBBBBWWPPWWBBBBDO..",
        "..OSSSSSWWPPWWSSSSWWPPWWSSSSDO..",
        "..OBBBBBBWWWWBBBBBBWWWWBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBCCBBBBBBBBBBBBBBBBCCBBDO..",
        "..OSSSSSSSSSMSSSSSSMSSSSSSSSDO..",
        "..OBBBBBBBBBBMMMMMMBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBBBBBBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBBBBBBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "...OBBBBBBDOOBBBBBDOOBBBBBBDO...",
        "....OSSSSDO..OSSSDO..OSSSSDO....",
        "....OBBBBDO..OBBBDO..OBBBBDO....",
        ".....OOOOO....OOOO....OOOOO.....",
        "................................",
        "................................",
    ),
    "idle2" to listOf(
        "................................",
        "................................",
        "...........OOOOOOOOOO...........",
        ".........OOBBBBBBBBBDOO.........",
        "........OSSSSSSSSSSSSSDO........",
        ".......OBBBBBBBBBBBBBBBDO.......",
        "......OSHHHSSSSSSSSSSSSSDO......",
        ".....OBHHBBBBBBBBBBBBBBBBDO.....",
        "....OSSHSSSSSSSSSSSSSSSSSSDO....",
        "....OBHBBBBBBBBBBBBBBBBBBBDO....",
        "...OSSHSSSSSSSSSSSSSSSSSSSSDO...",
        "...OBBBBBWWWWBBBBBBWWWWBBBBDO...",
        "...OSSSSWWWWWWSSSSWWWWWWSSSDO...",
        "...OBBBBWWWWWWBBBBWWWWWWBBBDO...",
        "...OSSSSWWLPWWSSSSWWLPWWSSSDO...",
        "..OBBBBBWWPPWWBBBBWWPPWWBBBBDO..",
        "..OSSSSSWWPPWWSSSSWWPPWWSSSSDO..",
        "..OBBBBBBWWWWBBBBBBWWWWBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBCCBBBBBBBBBBBBBBBBCCBBDO..",
        "..OSSSSSSSSSMSSSSSSMSSSSSSSSDO..",
        "..OBBBBBBBBBBMMMMMMBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBBBBBBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBBBBBBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBDOOBBBBBDOOBBBBBDOOBBBDO..",
        "..OSSDO..OSSSDO..OSSSDO..OSSDO..",
        "..OBBDO..OBBBDO..OBBBDO..OBBDO..",
        "...OOO....OOOO....OOOO....OOO...",
        "................................",
    ),
    "blink" to listOf(
        "................................",
        "...........OOOOOOOOOO...........",
        ".........OOBBBBBBBBBDOO.........",
        "........OSSSSSSSSSSSSSDO........",
        ".......OBBBBBBBBBBBBBBBDO.......",
        "......OSHHHSSSSSSSSSSSSSDO......",
        ".....OBHHBBBBBBBBBBBBBBBBDO.....",
        "....OSSHSSSSSSSSSSSSSSSSSSDO....",
        "....OBHBBBBBBBBBBBBBBBBBBBDO....",
        "...OSSHSSSSSSSSSSSSSSSSSSSSDO...",
        "...OBBBBBBBBBBBBBBBBBBBBBBBDO...",
        "...OSSSSSSSSSSSSSSSSSSSSSSSDO...",
        "...OBBBBBBMMBBBBBBBBMMBBBBBDO...",
        "...OSSSSSMSSMSSSSSSMSSMSSSSDO...",
        "..OBBBBBMBBBBMBBBBMBBBBMBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBBBBBBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBCCBBBBBBBBBBBBBBBBCCBBDO..",
        "..OSSSSSSSSSMSSSSSSMSSSSSSSSDO..",
        "..OBBBBBBBBBBMMMMMMBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBBBBBBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBBBBBBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "...OBBBBBBDOOBBBBBDOOBBBBBBDO...",
        "....OSSSSDO..OSSSDO..OSSSSDO....",
        "....OBBBBDO..OBBBDO..OBBBBDO....",
        ".....OOOOO....OOOO....OOOOO.....",
        "................................",
        "................................",
    ),
    "talk1" to listOf(
        "................................",
        "...........OOOOOOOOOO...........",
        ".........OOBBBBBBBBBDOO.........",
        "........OSSSSSSSSSSSSSDO........",
        ".......OBBBBBBBBBBBBBBBDO.......",
        "......OSHHHSSSSSSSSSSSSSDO......",
        ".....OBHHBBBBBBBBBBBBBBBBDO.....",
        "....OSSHSSSSSSSSSSSSSSSSSSDO....",
        "....OBHBBBBBBBBBBBBBBBBBBBDO....",
        "...OSSHSSSSSSSSSSSSSSSSSSSSDO...",
        "...OBBBBBWWWWBBBBBBWWWWBBBBDO...",
        "...OSSSSWWWWWWSSSSWWWWWWSSSDO...",
        "...OBBBBWWWWWWBBBBWWWWWWBBBDO...",
        "...OSSSSWWLPWWSSSSWWLPWWSSSDO...",
        "..OBBBBBWWPPWWBBBBWWPPWWBBBBDO..",
        "..OSSSSSWWPPWWSSSSWWPPWWSSSSDO..",
        "..OBBBBBBWWWWBBBBBBWWWWBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBCCBBBBBBBBBBBBBBBBCCBBDO..",
        "..OSSSSSSSSSSMMMMMMSSSSSSSSSDO..",
        "..OBBBBBBBBBMMMMMMMMBBBBBBBBDO..",
        "..OSSSSSSSSSMMMTTMMMSSSSSSSSDO..",
        "..OBBBBBBBBBBMTTTTMBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBBBBBBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "...OBBBBBBDOOBBBBBDOOBBBBBBDO...",
        "....OSSSSDO..OSSSDO..OSSSSDO....",
        "....OBBBBDO..OBBBDO..OBBBBDO....",
        ".....OOOOO....OOOO....OOOOO.....",
        "................................",
        "................................",
    ),
    "talk2" to listOf(
        "................................",
        "................................",
        "...........OOOOOOOOOO...........",
        ".........OOBBBBBBBBBDOO.........",
        "........OSSSSSSSSSSSSSDO........",
        ".......OBBBBBBBBBBBBBBBDO.......",
        "......OSHHHSSSSSSSSSSSSSDO......",
        ".....OBHHBBBBBBBBBBBBBBBBDO.....",
        "....OSSHSSSSSSSSSSSSSSSSSSDO....",
        "....OBHBBBBBBBBBBBBBBBBBBBDO....",
        "...OSSHSSSSSSSSSSSSSSSSSSSSDO...",
        "...OBBBBBWWWWBBBBBBWWWWBBBBDO...",
        "...OSSSSWWWWWWSSSSWWWWWWSSSDO...",
        "...OBBBBWWWWWWBBBBWWWWWWBBBDO...",
        "...OSSSSWWLPWWSSSSWWLPWWSSSDO...",
        "..OBBBBBWWPPWWBBBBWWPPWWBBBBDO..",
        "..OSSSSSWWPPWWSSSSWWPPWWSSSSDO..",
        "..OBBBBBBWWWWBBBBBBWWWWBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBCCBBBBBBBBBBBBBBBBCCBBDO..",
        "..OSSSSSSSSSSSSMMSSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBMMMMBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSMMMMSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBBMMBBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBBBBBBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBDOOBBBBBDOOBBBBBDOOBBBDO..",
        "..OSSDO..OSSSDO..OSSSDO..OSSDO..",
        "..OBBDO..OBBBDO..OBBBDO..OBBDO..",
        "...OOO....OOOO....OOOO....OOO...",
        "................................",
    ),
    "think1" to listOf(
        "................................",
        "...........OOOOOOOOOO...........",
        ".........OOBBBBBBBBBDOO.........",
        "........OSSSSSSSSSSSSSDO........",
        ".......OBBBBBBBBBBBBBBBDO.......",
        "......OSHHHSSSSSSSSSSSSSDO......",
        ".....OBHHBBBBBBBBBBBBBBBBDO.....",
        "....OSSHSSSSSSSSSSSSSSSSSSDO....",
        "....OBHBBBBBBBBBBBBBBBBBBBDO....",
        "...OSSHSSSSSSSSSSSSSSSSSSSSDO...",
        "...OBBBBBWWWWBBBBBBWWWWBBBBDO...",
        "...OSSSSWWWLPWSSSSWWWLPWSSSDO...",
        "...OBBBBWWWPPWBBBBWWWPPWBBBDO...",
        "...OSSSSWWWPPWSSSSWWWPPWSSSDO...",
        "..OBBBBBWWWWWWBBBBWWWWWWBBBBDO..",
        "..OSSSSSWWWWWWSSSSWWWWWWSSSSDO..",
        "..OBBBBBBWWWWBBBBBBWWWWBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBCCBBBBBBBBBBBBBBBBCCBBDO..",
        "..OSSSSSSSSSSSSMMSSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBMMMMBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSMMMMSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBBMMBBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBBBBBBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "...OBBBBBBDOOBBBBBDOOBBBBBBDO...",
        "....OSSSSDO..OSSSDO..OSSSSDO....",
        "....OBBBBDO..OBBBDO..OBBBBDO....",
        ".....OOOOO....OOOO....OOOOO.....",
        "................................",
        "................................",
    ),
    "think2" to listOf(
        "................................",
        "................................",
        "...........OOOOOOOOOO...........",
        ".........OOBBBBBBBBBDOO.........",
        "........OSSSSSSSSSSSSSDO........",
        ".......OBBBBBBBBBBBBBBBDO.......",
        "......OSHHHSSSSSSSSSSSSSDO......",
        ".....OBHHBBBBBBBBBBBBBBBBDO.....",
        "....OSSHSSSSSSSSSSSSSSSSSSDO....",
        "....OBHBBBBBBBBBBBBBBBBBBBDO....",
        "...OSSHSSSSSSSSSSSSSSSSSSSSDO...",
        "...OBBBBBWWWWBBBBBBWWWWBBBBDO...",
        "...OSSSSWLPWWWSSSSWLPWWWSSSDO...",
        "...OBBBBWPPWWWBBBBWPPWWWBBBDO...",
        "...OSSSSWPPWWWSSSSWPPWWWSSSDO...",
        "..OBBBBBWWWWWWBBBBWWWWWWBBBBDO..",
        "..OSSSSSWWWWWWSSSSWWWWWWSSSSDO..",
        "..OBBBBBBWWWWBBBBBBWWWWBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBCCBBBBBBBBBBBBBBBBCCBBDO..",
        "..OSSSSSSSSSSSSMMSSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBMMMMBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSMMMMSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBBMMBBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBBBBBBBBBBBBBBBBBBBBBBBDO..",
        "..OSSSSSSSSSSSSSSSSSSSSSSSSSDO..",
        "..OBBBDOOBBBBBDOOBBBBBDOOBBBDO..",
        "..OSSDO..OSSSDO..OSSSDO..OSSDO..",
        "..OBBDO..OBBBDO..OBBBDO..OBBDO..",
        "...OOO....OOOO....OOOO....OOO...",
        "................................",
    ),
)

enum class BitterMood { IDLE, TALKING, THINKING }

/** Frame sequence for a mood, with how long each frame shows (ms). */
fun bitterSequence(mood: BitterMood): List<Pair<String, Long>> = when (mood) {
    BitterMood.IDLE -> listOf("idle1" to 520L, "idle2" to 520L, "idle1" to 520L, "idle2" to 520L, "idle1" to 380L, "blink" to 140L, "idle1" to 520L, "idle2" to 520L)
    BitterMood.TALKING -> listOf("talk1" to 150L, "talk2" to 150L)
    BitterMood.THINKING -> listOf("think1" to 480L, "think2" to 480L)
}

@Composable
fun BitterSprite(size: Dp, mood: BitterMood = BitterMood.IDLE, modifier: Modifier = Modifier, animate: Boolean = true) {
    val p = LocalPalette.current
    var frame by remember { mutableStateOf("idle1") }
    LaunchedEffect(mood, animate) {
        if (!animate) { frame = "idle1"; return@LaunchedEffect }
        val seq = bitterSequence(mood)
        var i = 0
        while (true) {
            val (f, ms) = seq[i % seq.size]
            frame = f
            delay(ms)
            i++
        }
    }
    val body = p.bitter
    val colors = remember(body) {
        mapOf(
            'O' to darken(body, 0.15f),
            'B' to body,
            'S' to darken(body, 0.86f),
            'D' to darken(body, 0.62f),
            'H' to lerp(body, Color.White, 0.6f),
            'W' to Color.White,
            'P' to Color(0xFF0D47A1),
            'L' to Color(0xFF90CAF9),
            'M' to darken(body, 0.15f),
            'T' to Color(0xFFFF6F91),
            'C' to Color(0xFFFF8AB3),
        )
    }
    val rows = BITTER_FRAMES.getValue(frame)
    Canvas(modifier.size(size)) {
        val cell = this.size.width / 32f
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch ->
                colors[ch]?.let { drawRect(it, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f)) }
            }
        }
    }
}

/** Bitter on his dark pixel tile, as an avatar. */
@Composable
fun BitterAvatar(size: Dp, mood: BitterMood = BitterMood.IDLE, animate: Boolean = true) {
    val p = LocalPalette.current
    val shape = bitShape(size / 6)
    Box(
        Modifier.size(size).clip(shape).background(Color(0xFF15152A)).border((size / 20).coerceAtLeast(2.dp), darken(p.bitter, 0.45f), shape),
        contentAlignment = Alignment.Center,
    ) { BitterSprite(size * 0.86f, mood, Modifier.padding(top = size * 0.04f), animate) }
}

/** The bitmessenger logo: a 32x32 pixel speech bubble with three typing dots. */
val MESSENGER_LOGO = listOf(
    "................................",
    "................................",
    "................................",
    "................................",
    "......OOOOOOOOOOOOOOOOOOOO......",
    ".....OLLLLLLLLLLLLLLLLLLLLO.....",
    "....OLBBBBBBBBBBBBBBBBBBBBLO....",
    "...OLBBBBBBBBBBBBBBBBBBBBBBLO...",
    "..OLBBBBBBBBBBBBBBBBBBBBBBBBLO..",
    "..OLBBBBBBBBBBBBBBBBBBBBBBBBDO..",
    "..OLBBBBBBBBBBBBBBBBBBBBBBBBDO..",
    "..OLBBBBBBBBBBBBBBBBBBBBBBBBDO..",
    "..OLBBBGWWWBBBGWWWBBBGWWWBBBDO..",
    "..OLBBBWWWWBBBWWWWBBBWWWWBBBDO..",
    "..OLBBBWWWWBBBWWWWBBBWWWWBBBDO..",
    "..OLBBBWWWWBBBWWWWBBBWWWWBBBDO..",
    "..OLBBBBBBBBBBBBBBBBBBBBBBBBDO..",
    "..OLBBBBBBBBBBBBBBBBBBBBBBBBDO..",
    "..OLBBBBBBBBBBBBBBBBBBBBBBBBDO..",
    "..OLBBBBBBBBBBBBBBBBBBBBBBBBDO..",
    "...OLBBBBBBBBBBBBBBBBBBBBBBDO...",
    "....OLBBBBBBBBBBBBBBBBBBBBDO....",
    ".....OLBBBBBDDDDDDDDDDDDDDO.....",
    "......OLBBDDOOOOOOOOOOOOOO......",
    ".....OLBDDOO....................",
    "....OLDDOO......................",
    "...OLDOO........................",
    "....OO..........................",
    "................................",
    "................................",
    "................................",
    "................................",
)

@Composable
fun MessengerLogo(size: Dp, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val body = p.logo
    val colors = mapOf(
        'O' to darken(body, 0.15f), 'B' to body, 'L' to lerp(body, Color.White, 0.5f), 'D' to darken(body, 0.7f),
        'W' to Color.White, 'G' to p.waves,
    )
    Canvas(modifier.size(size)) {
        val cell = this.size.width / 32f
        MESSENGER_LOGO.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch -> colors[ch]?.let { drawRect(it, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f)) } }
        }
    }
}
