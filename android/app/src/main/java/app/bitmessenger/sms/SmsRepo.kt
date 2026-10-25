package app.bitmessenger.sms

import android.Manifest
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.PhoneNumberUtils
import android.telephony.SmsManager
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class Person(val address: String, val name: String?, val photoUri: String?) {
    val label: String get() = name ?: Phones.pretty(address)
}

data class Conversation(
    val threadId: Long,
    val people: List<Person>,
    val snippet: String,
    val date: Long,
    val unread: Int,
    val fromMe: Boolean,
) {
    val title: String get() = people.joinToString(", ") { it.label }.ifBlank { "Unknown" }
    val group: Boolean get() = people.size > 1
}

enum class MsgState { RECEIVED, SENDING, SENT, DELIVERED, FAILED }

data class Msg(
    val id: Long,
    val mms: Boolean,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val fromMe: Boolean,
    val state: MsgState,
    val read: Boolean,
    val imageUri: String? = null,
)

object Phones {
    fun key(number: String?): String = number.orEmpty().filter { it.isDigit() }.takeLast(10)

    fun pretty(number: String?): String {
        if (number.isNullOrBlank()) return "Unknown"
        if (number.any { it.isLetter() }) return number // short codes / alphanumeric senders
        return PhoneNumberUtils.formatNumber(number, Locale.getDefault().country) ?: number
    }
}

/**
 * Reads and writes the phone's SMS/MMS store (the same one every texting app uses). Writing is
 * only allowed while bitmessenger is the default SMS app; otherwise Android writes sent texts for
 * us and we stay read-only for everything else.
 */
object SmsRepo {
    private val CONVERSATIONS: Uri = Uri.parse("content://mms-sms/conversations?simple=true")
    private val CANONICAL: Uri = Uri.parse("content://mms-sms/canonical-addresses")
    const val ACTION_SENT = "app.bitmessenger.SMS_SENT"
    const val ACTION_DELIVERED = "app.bitmessenger.SMS_DELIVERED"

    private val people = ConcurrentHashMap<String, Person>()

    fun isDefault(context: Context) = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

    fun canRead(context: Context) = context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    fun person(context: Context, address: String): Person = people.getOrPut(Phones.key(address).ifEmpty { address }) {
        var name: String? = null
        var photo: String? = null
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED && address.isNotBlank()) {
            runCatching {
                val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
                context.contentResolver.query(
                    uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_URI), null, null, null,
                )?.use { c -> if (c.moveToFirst()) { name = c.getString(0); photo = c.getString(1) } }
            }
        }
        Person(address, name, photo)
    }

    fun forgetPeople() = people.clear()

    private fun canonicalAddresses(context: Context): Map<Long, String> {
        val out = HashMap<Long, String>()
        context.contentResolver.query(CANONICAL, arrayOf("_id", "address"), null, null, null)?.use { c ->
            while (c.moveToNext()) out[c.getLong(0)] = c.getString(1).orEmpty()
        }
        return out
    }

    private fun unreadCounts(context: Context): Map<Long, Int> {
        val out = HashMap<Long, Int>()
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.THREAD_ID),
            "${Telephony.Sms.READ} = 0 AND ${Telephony.Sms.TYPE} = ${Telephony.Sms.MESSAGE_TYPE_INBOX}", null, null,
        )?.use { c -> while (c.moveToNext()) out.merge(c.getLong(0), 1, Int::plus) }
        context.contentResolver.query(Telephony.Mms.CONTENT_URI, arrayOf(Telephony.Mms.THREAD_ID), "${Telephony.Mms.READ} = 0", null, null)
            ?.use { c -> while (c.moveToNext()) out.merge(c.getLong(0), 1, Int::plus) }
        return out
    }

    /** Whether the newest SMS in each thread was sent by us (for the "You:" prefix). */
    private fun lastFromMe(context: Context): Map<Long, Boolean> {
        val out = HashMap<Long, Boolean>()
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.TYPE), null, null, "${Telephony.Sms.DATE} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val t = c.getLong(0)
                if (t !in out) out[t] = c.getInt(1) != Telephony.Sms.MESSAGE_TYPE_INBOX
            }
        }
        return out
    }

    fun conversations(context: Context): List<Conversation> {
        if (!canRead(context)) return emptyList()
        return runCatching {
            val addresses = canonicalAddresses(context)
            val unread = unreadCounts(context)
            val mine = lastFromMe(context)
            val out = ArrayList<Conversation>()
            context.contentResolver.query(
                CONVERSATIONS, arrayOf("_id", "date", "snippet", "recipient_ids"), null, null, "date DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val ids = c.getString(3).orEmpty().split(' ').mapNotNull { it.toLongOrNull() }
                    val ppl = ids.mapNotNull { addresses[it] }.filter { it.isNotBlank() }.map { person(context, it) }
                    out += Conversation(id, ppl, c.getString(2).orEmpty(), c.getLong(1), unread[id] ?: 0, mine[id] ?: false)
                }
            }
            out
        }.getOrDefault(emptyList())
    }

    fun messages(context: Context, threadId: Long, limit: Int = 400): List<Msg> {
        if (!canRead(context)) return emptyList()
        val out = ArrayList<Msg>()
        runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.STATUS, Telephony.Sms.READ),
                "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()), "${Telephony.Sms.DATE} DESC LIMIT $limit",
            )?.use { c ->
                while (c.moveToNext()) {
                    val type = c.getInt(4)
                    val state = when (type) {
                        Telephony.Sms.MESSAGE_TYPE_INBOX -> MsgState.RECEIVED
                        Telephony.Sms.MESSAGE_TYPE_FAILED -> MsgState.FAILED
                        Telephony.Sms.MESSAGE_TYPE_OUTBOX, Telephony.Sms.MESSAGE_TYPE_QUEUED -> MsgState.SENDING
                        else -> if (c.getInt(5) == Telephony.Sms.STATUS_COMPLETE) MsgState.DELIVERED else MsgState.SENT
                    }
                    out += Msg(c.getLong(0), false, threadId, c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getLong(3),
                        type != Telephony.Sms.MESSAGE_TYPE_INBOX, state, c.getInt(6) == 1)
                }
            }
        }
        out += runCatching { mmsMessages(context, threadId, limit) }.getOrDefault(emptyList())
        return out.sortedBy { it.date }.takeLast(limit)
    }

    /** Picture/group messages: text and the first image of each, read-only. */
    private fun mmsMessages(context: Context, threadId: Long, limit: Int): List<Msg> {
        val out = ArrayList<Msg>()
        context.contentResolver.query(
            Telephony.Mms.CONTENT_URI, arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX, Telephony.Mms.READ),
            "${Telephony.Mms.THREAD_ID} = ?", arrayOf(threadId.toString()), "${Telephony.Mms.DATE} DESC LIMIT $limit",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val box = c.getInt(2)
                var text = ""
                var image: String? = null
                context.contentResolver.query(Uri.parse("content://mms/part"), arrayOf("_id", "ct", "text"), "mid = ?", arrayOf(id.toString()), null)
                    ?.use { p ->
                        while (p.moveToNext()) {
                            val ct = p.getString(1).orEmpty()
                            when {
                                ct == "text/plain" -> text += p.getString(2).orEmpty()
                                ct.startsWith("image/") && image == null -> image = "content://mms/part/${p.getLong(0)}"
                            }
                        }
                    }
                val from = mmsSender(context, id)
                val mine = box != Telephony.Mms.MESSAGE_BOX_INBOX
                out += Msg(id, true, threadId, from, text, c.getLong(1) * 1000, mine, if (mine) MsgState.SENT else MsgState.RECEIVED, c.getInt(3) == 1, image)
            }
        }
        return out
    }

    private fun mmsSender(context: Context, id: Long): String {
        context.contentResolver.query(Uri.parse("content://mms/$id/addr"), arrayOf("address", "type"), "type = 137", null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0).orEmpty()
        }
        return ""
    }

    fun search(context: Context, query: String): Set<Long> {
        if (!canRead(context) || query.isBlank()) return emptySet()
        val out = HashSet<Long>()
        runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.THREAD_ID), "${Telephony.Sms.BODY} LIKE ?", arrayOf("%$query%"), null,
            )?.use { c -> while (c.moveToNext()) out += c.getLong(0) }
        }
        return out
    }

    fun threadFor(context: Context, addresses: List<String>): Long =
        Telephony.Threads.getOrCreateThreadId(context, addresses.toSet())

    /**
     * Sends a text to every address. Group texts go out as separate SMS to each person (bitmessenger
     * can't send group MMS yet). As default app we record the message ourselves; otherwise Android does.
     */
    fun send(context: Context, addresses: List<String>, body: String): Boolean {
        if (body.isBlank() || addresses.isEmpty()) return false
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return false
        val sms = context.getSystemService(SmsManager::class.java) ?: return false
        val default = isDefault(context)
        var ok = true
        addresses.forEach { to ->
            val uri = if (default) runCatching {
                context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, to)
                    put(Telephony.Sms.BODY, body)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.SEEN, 1)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
                    put(Telephony.Sms.THREAD_ID, threadFor(context, listOf(to)))
                })
            }.getOrNull() else null
            val parts = sms.divideMessage(body)
            val sent = ArrayList(parts.map { status(context, ACTION_SENT, uri) })
            val delivered = ArrayList(parts.map { status(context, ACTION_DELIVERED, uri) })
            ok = ok && runCatching { sms.sendMultipartTextMessage(to, null, parts, sent, delivered) }.isSuccess
        }
        return ok
    }

    private var requestCode = 0

    private fun status(context: Context, action: String, uri: Uri?): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode++,
            Intent(action, uri, context, SmsStatusReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun setStatus(context: Context, uri: Uri, type: Int? = null, status: Int? = null) {
        runCatching {
            context.contentResolver.update(uri, ContentValues().apply {
                type?.let { put(Telephony.Sms.TYPE, it) }
                status?.let { put(Telephony.Sms.STATUS, it) }
            }, null, null)
        }
    }

    /** Saves an incoming text (default app only) and returns its thread id. */
    fun saveIncoming(context: Context, from: String, body: String, sentAt: Long): Long {
        val thread = threadFor(context, listOf(from))
        runCatching {
            context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, ContentValues().apply {
                put(Telephony.Sms.ADDRESS, from)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.DATE_SENT, sentAt)
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.SEEN, 0)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                put(Telephony.Sms.THREAD_ID, thread)
            })
        }
        return thread
    }

    fun markRead(context: Context, threadId: Long) {
        if (!isDefault(context)) return
        runCatching {
            val v = ContentValues().apply { put(Telephony.Sms.READ, 1); put(Telephony.Sms.SEEN, 1) }
            context.contentResolver.update(Telephony.Sms.CONTENT_URI, v, "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0", arrayOf(threadId.toString()))
            context.contentResolver.update(Telephony.Mms.CONTENT_URI, ContentValues().apply { put(Telephony.Mms.READ, 1) },
                "${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.READ} = 0", arrayOf(threadId.toString()))
        }
        Notifier.cancel(context, threadId)
    }

    fun markAllRead(context: Context) {
        if (!isDefault(context)) return
        runCatching {
            context.contentResolver.update(Telephony.Sms.CONTENT_URI, ContentValues().apply { put(Telephony.Sms.READ, 1); put(Telephony.Sms.SEEN, 1) },
                "${Telephony.Sms.READ} = 0", null)
            context.contentResolver.update(Telephony.Mms.CONTENT_URI, ContentValues().apply { put(Telephony.Mms.READ, 1) }, "${Telephony.Mms.READ} = 0", null)
        }
        Notifier.cancelAll(context)
    }

    /** Deletes a whole thread for good (default app only). Returns false when Android won't allow it. */
    fun deleteThread(context: Context, threadId: Long): Boolean {
        if (!isDefault(context)) return false
        return runCatching {
            context.contentResolver.delete(Uri.withAppendedPath(Telephony.Threads.CONTENT_URI, threadId.toString()), null, null) >= 0
        }.getOrDefault(false)
    }
}
