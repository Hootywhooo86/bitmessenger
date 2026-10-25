package app.bitmessenger

import android.Manifest
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.bitmessenger.bitter.BitterMsg
import app.bitmessenger.bitter.BitterStore
import app.bitmessenger.sms.Conversation
import app.bitmessenger.sms.Msg
import app.bitmessenger.sms.MsgState
import app.bitmessenger.sms.Person
import app.bitmessenger.ui.BitTheme
import app.bitmessenger.ui.BitterScreen
import app.bitmessenger.ui.Bubble
import app.bitmessenger.ui.Composer
import app.bitmessenger.ui.HomeScreen
import app.bitmessenger.ui.Section
import app.bitmessenger.ui.SettingsScreen
import app.bitmessenger.ui.TextingHost
import app.bitmessenger.ui.TopBar
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the main screens to PNGs (app/screenshots/) with made-up chats, so the look can be
 * checked without a phone. Run: ./gradlew recordRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val host = object : TextingHost {
        override val defaultApp = MutableStateFlow(true)
        override fun requestDefaultSms() {}
        override fun askMissingPermissions() {}
    }

    private val now = System.currentTimeMillis()
    private fun person(name: String) = Person("555010${name.length}", name, null)
    private val chats = listOf(
        Conversation(1, listOf(person("Player Two")), "On my way!", now - 8 * 60_000, 0, true),
        Conversation(2, listOf(person("Mom")), "It's ok I need to get up anyways", now - 3 * 3_600_000, 1, false),
        Conversation(3, listOf(person("Ava"), person("Sam"), person("Mom")), "Ava: Why", now - 5 * 3_600_000, 0, false),
        Conversation(4, listOf(person("Sam")), "https://vt.tiktok.com/ZS…", now - 9 * 3_600_000, 9, false),
        Conversation(5, listOf(Person("87226", null, null)), "URBAN PLANET: TMRW! 1 DAY ONLY!", now - 2 * 86_400_000, 1, false),
        Conversation(6, listOf(person("Dakota")), "I forgot you have an iPhone", now - 4 * 86_400_000, 0, true),
    )

    @Before fun setUp() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(app).grantPermissions(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS)
        BitterStore.clear()
        compose.mainClock.autoAdvance = false // Bitter animates forever; freeze time for the shot
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent { BitTheme { content() } }
        compose.mainClock.advanceTimeBy(100)
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test fun home() = shot("home") {
        app.bitmessenger.data.Settings.update { it.copy(pinned = setOf(1L, 2L)) }
        HomeScreen(host, chats, open = {}, refresh = {})
    }

    @Test fun bitterChat() {
        BitterStore.add(BitterMsg("What should I make for dinner?", true, now - 60_000))
        BitterStore.add(BitterMsg("Ooh, how about tacos? 🌮 Quick, cheap, and everyone builds their own. Want a 20-minute recipe?", false, now - 50_000, "Claude"))
        BitterStore.add(BitterMsg("Yes please!", true, now - 10_000))
        shot("bitter") { BitterScreen(onBack = {}, openSettings = {}) }
    }

    @Test fun thread() = shot("thread") {
        Column {
            TopBar("Player Two", onBack = {})
            Column(Modifier.padding(12.dp)) {
                Bubble(Msg(1, false, 1, "5550100", "Are you coming to the game?", now - 600_000, false, MsgState.RECEIVED, true), null, true)
                Bubble(Msg(2, false, 1, "5550100", "Coming", now - 480_000, true, MsgState.DELIVERED, true), null, true)
                Bubble(Msg(3, false, 1, "5550100", "Bring snacks 🍿", now - 300_000, false, MsgState.RECEIVED, true), null, true)
            }
            Composer("Be there in 10", {}) {}
        }
    }

    @Test fun themeEditor() = shot("theme") { SettingsScreen(host, Section.THEME, onBack = {}) }

    @Test fun bitterSettings() = shot("bitter_settings") { SettingsScreen(host, Section.BITTER, onBack = {}) }
}
