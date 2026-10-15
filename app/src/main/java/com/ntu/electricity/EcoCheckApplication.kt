// Modified by Xushengming222, 2026-10-15: electricity payment enhancement; see CHANGES.md.
package com.ntu.electricity

import android.app.Application
import com.ntu.electricity.data.local.AppDatabase
import com.ntu.electricity.data.local.UserPreferences
import com.ntu.electricity.network.NtuHttpClient
import com.ntu.electricity.network.RoomDataLoader
import com.ntu.electricity.payment.LoginAttemptGate

class EcoCheckApplication : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var httpClient: NtuHttpClient
        private set
    lateinit var roomDataLoader: RoomDataLoader
        private set
    lateinit var userPreferences: UserPreferences
        private set
    lateinit var loginAttemptGate: LoginAttemptGate
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        httpClient = NtuHttpClient(this)
        roomDataLoader = RoomDataLoader(this)
        userPreferences = UserPreferences(this)
        val loginGuardPreferences = getSharedPreferences("automatic_login_guard", MODE_PRIVATE)
        loginAttemptGate = LoginAttemptGate(
            read = { key -> loginGuardPreferences.getLong(key, 0L) },
            write = { key, timestamp -> loginGuardPreferences.edit().putLong(key, timestamp).commit() }
        )
    }
}
