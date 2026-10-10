package com.hsrp.dealer

import android.app.Application
import com.hsrp.dealer.data.ApiClient
import com.hsrp.dealer.data.AppDb
import com.hsrp.dealer.data.HsrpApi
import com.hsrp.dealer.data.Settings
import com.hsrp.dealer.sync.SyncQueue

class HsrpApp : Application() {
    override fun onCreate() {
        super.onCreate()
        App.settings = Settings(this)
        App.db = AppDb.get(this)
        // Send anything recorded offline as soon as the app starts with a connection.
        SyncQueue.kick(this)
    }
}

/** Process-wide singletons for this small app. */
object App {
    lateinit var settings: Settings
    lateinit var db: AppDb
    suspend fun api(): HsrpApi = ApiClient.get(settings)
}
