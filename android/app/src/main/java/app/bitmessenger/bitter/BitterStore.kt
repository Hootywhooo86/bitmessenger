package app.bitmessenger.bitter

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class BitterMsg(val text: String, val fromMe: Boolean, val at: Long, val brain: String? = null)

/** Bitter's chat history, kept on the phone only (files/bitter_chat.json), newest 300 messages. */
object BitterStore {
    private const val MAX = 300
    private var file: File? = null
    private val _msgs = MutableStateFlow<List<BitterMsg>>(emptyList())
    val msgs: StateFlow<List<BitterMsg>> = _msgs

    fun init(context: Context) {
        val f = File(context.filesDir, "bitter_chat.json").also { file = it }
        _msgs.value = runCatching {
            val a = JSONArray(f.readText())
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                BitterMsg(o.getString("t"), o.getBoolean("me"), o.getLong("at"), o.optString("b").ifBlank { null })
            }
        }.getOrDefault(emptyList())
    }

    fun add(m: BitterMsg) = save((_msgs.value + m).takeLast(MAX))

    fun clear() = save(emptyList())

    private fun save(list: List<BitterMsg>) {
        _msgs.value = list
        val a = JSONArray()
        list.forEach { m -> a.put(JSONObject().put("t", m.text).put("me", m.fromMe).put("at", m.at).put("b", m.brain ?: "")) }
        runCatching { file?.writeText(a.toString()) }
    }
}
