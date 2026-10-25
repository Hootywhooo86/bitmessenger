package app.bitmessenger.bitter

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import app.bitmessenger.data.BitSettings
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Where Bitter's words come from on this reply. */
enum class Brain(val label: String) {
    FAMILY("on-device · via Bitcall"),
    CLAUDE("Claude"),
    OFFLINE("offline"),
}

data class BitterReply(val text: String, val brain: Brain, val note: String? = null)

/**
 * Bitter's brain, in order of preference (AUTO): bitcall's on-device model (private, free, needs
 * Bitcall's AI turned on and the same family signing key), then Claude with the user's own API key,
 * then a friendly offline fallback that explains how to wake him up.
 */
object BitterBrain {
    const val MODEL = "claude-opus-5-5"
    private const val BITCALL = "app.bitcall"
    private const val FAMILY_ACTION = "app.bit128.family.AI_GENERATE"

    fun system(name: String) = """
        You are $name, the pixel ghost mascot of the 128bit family of apps, chatting by text message inside bitmessenger on the user's phone.
        Talk like a warm, playful friend texting back: short replies (usually 1-4 sentences), plain text, no markdown, the odd retro-game joke or emoji is fine.
        Help with anything the user asks: questions, ideas, plans, drafting a text to someone, cheering them up.
        If you don't know something current (news, prices, weather), say so honestly instead of guessing.
    """.trimIndent()

    /** Streams [onText] with the reply so far as it grows; returns the finished reply. */
    suspend fun reply(context: Context, s: BitSettings, history: List<BitterMsg>, onText: (String) -> Unit): BitterReply {
        val mode = s.bitterBrain
        var note: String? = null
        if (mode == "AUTO" || mode == "FAMILY") {
            val r = family(context, s.bitterName, history)
            if (r.text != null) { typeOut(r.text, onText); return BitterReply(r.text, Brain.FAMILY) }
            note = r.why
            if (mode == "FAMILY") return offline(onText, note)
        }
        if ((mode == "AUTO" || mode == "CLAUDE") && s.claudeKey.isNotBlank()) {
            val r = claude(s.claudeKey, s.bitterName, history, onText)
            if (r.text != null) return BitterReply(r.text, Brain.CLAUDE)
            note = r.why
        } else if (mode == "CLAUDE") note = "Add your Claude API key in Settings → Bitter."
        return offline(onText, note)
    }

    private data class Attempt(val text: String?, val why: String?)

    private suspend fun typeOut(text: String, onText: (String) -> Unit) {
        // The on-device model answers in one go; reveal it a few letters at a time so Bitter "talks".
        var i = 0
        while (i < text.length) {
            i = minOf(text.length, i + 3)
            onText(text.substring(0, i))
            delay(18)
        }
    }

    private suspend fun offline(onText: (String) -> Unit, note: String?): BitterReply {
        val text = "Boo! 👻 My brain's switched off right now. Turn on Bitcall's on-device AI, or add a Claude API key in Settings → Bitter, and I'll be able to chat about anything."
        typeOut(text, onText)
        return BitterReply(text, Brain.OFFLINE, note)
    }

    /** The conversation flattened for the on-device model, newest last, trimmed to fit. */
    fun transcript(history: List<BitterMsg>, name: String, maxChars: Int = 3000): String {
        val lines = history.map { (if (it.fromMe) "User: " else "$name: ") + it.text }
        val kept = ArrayDeque<String>()
        var size = 0
        for (line in lines.asReversed()) {
            if (size + line.length > maxChars && kept.isNotEmpty()) break
            kept.addFirst(line); size += line.length + 1
        }
        return kept.joinToString("\n") + "\n$name:"
    }

    private suspend fun family(context: Context, name: String, history: List<BitterMsg>): Attempt {
        val intent = Intent(FAMILY_ACTION).setPackage(BITCALL)
        if (context.packageManager.queryIntentServices(intent, 0).isEmpty()) return Attempt(null, "Bitcall isn't installed.")
        return withTimeoutOrNull(120_000) {
            suspendCancellableCoroutine { cont ->
                var conn: ServiceConnection? = null
                fun finish(a: Attempt) {
                    conn?.let { runCatching { context.unbindService(it) } }
                    conn = null
                    if (cont.isActive) cont.resume(a)
                }
                val replies = Messenger(Handler(Looper.getMainLooper()) { msg ->
                    val d = msg.data
                    val text = d.getString("text")?.trim()
                    finish(if (d.getBoolean("ok") && !text.isNullOrBlank()) Attempt(text.removePrefix("$name:").trim(), null) else Attempt(null, familyError(d.getString("error"))))
                    true
                })
                conn = object : ServiceConnection {
                    override fun onServiceConnected(n: ComponentName, binder: IBinder) {
                        val m = Message.obtain(null, 1).apply {
                            replyTo = replies
                            data = Bundle().apply {
                                putString("system", system(name))
                                putString("user", transcript(history, name))
                                putInt("maxTokens", 300)
                                putFloat("temperature", 0.7f)
                            }
                        }
                        runCatching { Messenger(binder).send(m) }.onFailure { finish(Attempt(null, "Couldn't reach Bitcall.")) }
                    }
                    override fun onServiceDisconnected(n: ComponentName) = finish(Attempt(null, "Bitcall stopped answering."))
                }
                val bound = runCatching { context.bindService(intent, conn!!, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
                if (!bound) finish(Attempt(null, "Couldn't reach Bitcall."))
                cont.invokeOnCancellation { conn?.let { c -> runCatching { context.unbindService(c) } } }
            }
        } ?: Attempt(null, "Bitcall's AI took too long.")
    }

    /**
     * The chat as API turns: starts with the user, roles alternate (back-to-back messages from the
     * same side are joined), and it ends on the user's latest message. Keeps the newest [max] turns.
     */
    fun turns(history: List<BitterMsg>, max: Int = 40): List<Pair<Boolean, String>> {
        val out = ArrayList<Pair<Boolean, String>>()
        history.filter { it.text.isNotBlank() }.forEach { m ->
            if (out.isNotEmpty() && out.last().first == m.fromMe) out[out.lastIndex] = m.fromMe to (out.last().second + "\n" + m.text)
            else out += m.fromMe to m.text
        }
        while (out.isNotEmpty() && !out.last().first) out.removeAt(out.lastIndex)
        var kept = out.takeLast(max)
        while (kept.isNotEmpty() && !kept.first().first) kept = kept.drop(1)
        return kept
    }

    fun familyError(code: String?) = when (code) {
        "not_family" -> "This bitmessenger isn't signed with the 128bit family key, so Bitcall won't share its AI."
        "ai_off" -> "Bitcall's on-device AI is off (Bitcall → Settings → On-device AI)."
        "no_model" -> "Bitcall's AI model isn't downloaded yet."
        "in_call" -> "Bitcall is busy with a call."
        else -> "Bitcall's AI couldn't answer."
    }

    private suspend fun claude(key: String, name: String, history: List<BitterMsg>, onText: (String) -> Unit): Attempt = withContext(Dispatchers.IO) {
        val client = AnthropicOkHttpClient.builder().apiKey(key).build()
        try {
            val params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(2048L)
                .system(system(name))
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                // If a safety classifier declines, let the API retry on a suitable model by itself.
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .apply { turns(history).forEach { (mine, text) -> if (mine) addUserMessage(text) else addAssistantMessage(text) } }
                .build()
            val sb = StringBuilder()
            client.messages().createStreaming(params).use { stream ->
                stream.stream().forEach { event ->
                    event.contentBlockDelta().flatMap { it.delta().text() }.ifPresent { t ->
                        sb.append(t.text())
                        val now = sb.toString()
                        Handler(Looper.getMainLooper()).post { onText(now) }
                    }
                }
            }
            val text = sb.toString().trim()
            Attempt(text.ifBlank { null }, if (text.isBlank()) "Claude didn't say anything back." else null)
        } catch (e: UnauthorizedException) {
            Attempt(null, "Claude didn't accept that API key.")
        } catch (e: RateLimitException) {
            Attempt(null, "Claude is busy (rate limited). Try again in a moment.")
        } catch (e: AnthropicServiceException) {
            Attempt(null, "Claude error ${e.statusCode()}.")
        } catch (e: Throwable) {
            // Also catches linkage errors, so a library problem can't crash the chat.
            Attempt(null, "Couldn't reach Claude. Check your internet.")
        } finally {
            client.close()
        }
    }
}
