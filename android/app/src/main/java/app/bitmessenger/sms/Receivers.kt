package app.bitmessenger.sms

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Telephony
import android.telephony.TelephonyManager
import app.bitmessenger.data.Settings

/** Default-SMS-app delivery: only the default app gets SMS_DELIVER and must save the text itself. */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // A long text arrives in several parts from the same sender; join them back into one.
        parts.filterNotNull().groupBy { it.displayOriginatingAddress.orEmpty() }.forEach { (from, msgs) ->
            val body = msgs.joinToString("") { it.displayMessageBody.orEmpty() }
            val thread = SmsRepo.saveIncoming(context, from, body, msgs.first().timestampMillis)
            if (thread !in Settings.now.spam) Notifier.incoming(context, thread, SmsRepo.person(context, from).label, body)
        }
    }
}

/**
 * Picture and group messages (MMS). Downloading them needs carrier MMS handling that bitmessenger
 * doesn't have yet, so we let the user know one arrived rather than dropping it silently.
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        Notifier.mmsWaiting(context)
    }
}

/** Updates a sent text's state (sending → sent / failed → delivered) when the radio reports back. */
class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.data ?: return
        when (intent.action) {
            SmsRepo.ACTION_SENT -> SmsRepo.setStatus(
                context, uri,
                type = if (resultCode == Activity.RESULT_OK) Telephony.Sms.MESSAGE_TYPE_SENT else Telephony.Sms.MESSAGE_TYPE_FAILED,
            )
            SmsRepo.ACTION_DELIVERED -> SmsRepo.setStatus(context, uri, status = Telephony.Sms.STATUS_COMPLETE)
        }
    }
}

/** "Reply with a text" from the phone's incoming-call screen (required for the default SMS role). */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            val to = intent.data?.schemeSpecificPart?.substringBefore('?')?.split(',', ';')?.filter { it.isNotBlank() }.orEmpty()
            val body = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            if (to.isNotEmpty() && body.isNotBlank()) SmsRepo.send(this, to, body)
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
