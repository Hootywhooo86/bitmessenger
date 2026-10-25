package app.bitmessenger.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.bitmessenger.sms.Conversation
import app.bitmessenger.sms.SmsRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** Where the user is in the app. A tiny back stack is enough for a messenger. */
sealed interface Screen {
    data object Home : Screen
    data class Thread(val threadId: Long, val addresses: List<String> = emptyList(), val draft: String = "") : Screen
    data object Bitter : Screen
    data object NewChat : Screen
    data class Folder(val folder: app.bitmessenger.ui.Folder) : Screen
    data class Settings(val section: Section? = null) : Screen
}

/** What the screens need from the activity: texting-app status and the permission prompts. */
interface TextingHost {
    val defaultApp: StateFlow<Boolean>
    fun requestDefaultSms()
    fun askMissingPermissions()
}

class MainActivity : ComponentActivity(), TextingHost {
    companion object { const val EXTRA_THREAD = "thread" }

    /** Screens asked for from outside: a notification tap, an sms: link from bitcall's TEXT button. */
    val incoming = MutableStateFlow<Screen?>(null)
    override val defaultApp = MutableStateFlow(false)

    private val permissions = buildList {
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { changed.value++ }
    private val askRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        defaultApp.value = SmsRepo.isDefault(this)
        askMissingPermissions()
    }

    /** Bumped whenever the SMS store changes, so lists reload. */
    val changed = MutableStateFlow(0)
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { changed.value++ }
    }

    override fun requestDefaultSms() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_SMS) && !rm.isRoleHeld(RoleManager.ROLE_SMS)) askRole.launch(rm.createRequestRoleIntent(RoleManager.ROLE_SMS))
        else askMissingPermissions()
    }

    override fun askMissingPermissions() {
        if (permissions.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) askPermissions.launch(permissions)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        defaultApp.value = SmsRepo.isDefault(this)
        handle(intent)
        askMissingPermissions()
        runCatching { contentResolver.registerContentObserver(Uri.parse("content://mms-sms/"), true, observer) }
        setContent { BitTheme { App(this) } }
    }

    override fun onDestroy() {
        runCatching { contentResolver.unregisterContentObserver(observer) }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        defaultApp.value = SmsRepo.isDefault(this)
        SmsRepo.forgetPeople() // contact names/photos may have changed
        changed.value++
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        val thread = intent.getLongExtra(EXTRA_THREAD, -1L)
        if (thread >= 0) { incoming.value = Screen.Thread(thread); return }
        val data = intent.data ?: return
        if (data.scheme in setOf("sms", "smsto", "mms", "mmsto")) {
            val to = data.schemeSpecificPart.substringBefore('?').split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
            val body = intent.getStringExtra("sms_body") ?: intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            if (to.isNotEmpty()) incoming.value = Screen.Thread(runCatching { SmsRepo.threadFor(this, to) }.getOrDefault(-1L), to, body)
        }
    }
}

@Composable
fun App(activity: MainActivity) {
    val context = LocalContext.current
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }
    fun go(s: Screen) { stack = stack + s }
    fun back() { if (stack.size > 1) stack = stack.dropLast(1) }
    var conversations by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { activity.incoming.collect { s -> if (s != null) { stack = listOf(Screen.Home, s); activity.incoming.value = null } } }
    LaunchedEffect(Unit) { activity.changed.collect { tick++ } }
    LaunchedEffect(tick) { conversations = withContext(Dispatchers.IO) { SmsRepo.conversations(context) } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    BackHandler(stack.size > 1) { back() }

    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        when (val s = stack.last()) {
            Screen.Home -> HomeScreen(activity, conversations, open = ::go, refresh = { tick++ })
            is Screen.Thread -> ThreadScreen(s, conversations, onBack = ::back, refresh = { tick++ })
            Screen.Bitter -> BitterScreen(onBack = ::back, openSettings = { go(Screen.Settings(Section.BITTER)) })
            Screen.NewChat -> NewChatScreen(onBack = ::back, onOpen = { to ->
                val id = runCatching { SmsRepo.threadFor(context, to) }.getOrDefault(-1L)
                stack = stack.dropLast(1) + Screen.Thread(id, to)
            })
            is Screen.Folder -> FolderScreen(s.folder, conversations, onBack = ::back, open = ::go, refresh = { tick++ })
            is Screen.Settings -> SettingsScreen(activity, s.section, onBack = ::back)
        }
    }
}
