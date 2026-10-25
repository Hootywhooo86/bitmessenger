package app.bitmessenger

import android.app.Application
import app.bitmessenger.bitter.BitterStore
import app.bitmessenger.data.Settings
import app.bitmessenger.sms.Notifier

class BitmessengerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        BitterStore.init(this)
        Notifier.createChannels(this)
    }
}
