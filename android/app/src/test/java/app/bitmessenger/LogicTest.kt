package app.bitmessenger

import androidx.compose.ui.graphics.Color
import app.bitmessenger.bitter.BitterBrain
import app.bitmessenger.bitter.BitterMsg
import app.bitmessenger.ui.BITTER_FRAMES
import app.bitmessenger.ui.BitterMood
import app.bitmessenger.ui.ColorSlot
import app.bitmessenger.ui.MESSENGER_LOGO
import app.bitmessenger.ui.PALETTES
import app.bitmessenger.ui.bitterSequence
import app.bitmessenger.ui.with
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    private fun m(text: String, me: Boolean) = BitterMsg(text, me, 0L)

    @Test fun turnsStartWithUserAlternateAndEndWithUser() {
        val t = BitterBrain.turns(listOf(m("Boo! I'm Bitter", false), m("hi", true), m("there", true), m("Hello!", false), m("joke?", true)))
        assertEquals(listOf(true to "hi\nthere", false to "Hello!", true to "joke?"), t)
    }

    @Test fun turnsDropATrailingBitterReply() {
        val t = BitterBrain.turns(listOf(m("hi", true), m("Hello!", false)))
        assertEquals(listOf(true to "hi"), t)
    }

    @Test fun turnsKeepTheNewestAndStillStartWithUser() {
        val history = (1..30).flatMap { listOf(m("q$it", true), m("a$it", false)) } + m("last", true)
        val t = BitterBrain.turns(history, max = 6)
        assertTrue(t.first().first)
        assertEquals(true to "last", t.last())
        assertTrue(t.size <= 6)
    }

    @Test fun transcriptEndsWithBittersCue() {
        val s = BitterBrain.transcript(listOf(m("hi", true)), "Bitter")
        assertEquals("User: hi\nBitter:", s)
    }

    @Test fun everyBitterFrameIsA32x32MirroredSprite() {
        BITTER_FRAMES.forEach { (name, rows) ->
            assertEquals(name, 32, rows.size)
            rows.forEach { assertEquals(name, 32, it.length) }
            for (y in 0 until 32) for (x in 0 until 32) {
                assertEquals("$name ($x,$y)", rows[y][x] == '.', rows[y][31 - x] == '.')
            }
        }
    }

    @Test fun everyMoodUsesRealFrames() {
        BitterMood.entries.forEach { mood -> bitterSequence(mood).forEach { (f, ms) -> assertTrue(f in BITTER_FRAMES); assertTrue(ms > 0) } }
    }

    @Test fun logoIs32x32() {
        assertEquals(32, MESSENGER_LOGO.size)
        MESSENGER_LOGO.forEach { assertEquals(32, it.length) }
    }

    @Test fun messengerSlotsCanBeOverridden() {
        val blue = 0xFF0000FF
        val p = PALETTES[0].with(ColorSlot.entries.associate { it.name to blue })
        ColorSlot.entries.forEach { assertEquals(it.name, Color(blue), it.get(p)) }
    }

    @Test fun bitcallThemeCodesStillApply() {
        // A code made in bitcall overrides bitcall-only slots (DIGITS, HANGUP); they must not break the messenger.
        val p = PALETTES[0].with(mapOf("DIGITS" to 0xFFFF0000, "HANGUP" to 0xFF00FF00, "ACCENT" to 0xFF123456))
        assertEquals(Color(0xFF123456), p.accent)
    }
}
