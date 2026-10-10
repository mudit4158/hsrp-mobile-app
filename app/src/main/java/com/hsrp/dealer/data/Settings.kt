package com.hsrp.dealer.data

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hsrp.dealer.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "settings")

/** API base URL, session token and this device's id (registered with the server at sign-in). */
class Settings(private val context: Context) {
    private val baseUrlKey = stringPreferencesKey("baseUrl")
    private val tokenKey = stringPreferencesKey("token")
    private val deviceKey = stringPreferencesKey("deviceId")
    private val userKey = stringPreferencesKey("user")

    val token: Flow<String?> = context.dataStore.data.map { it[tokenKey] }
    val baseUrlFlow: Flow<String> = context.dataStore.data.map { it[baseUrlKey] ?: BuildConfig.DEFAULT_API_URL }
    val userFlow: Flow<String?> = context.dataStore.data.map { it[userKey] }

    suspend fun baseUrl(): String = baseUrlFlow.first()
    suspend fun token(): String? = token.first()

    suspend fun setBaseUrl(url: String) = context.dataStore.edit { it[baseUrlKey] = url.trim().trimEnd('/') }

    suspend fun setSession(token: String?, user: String?) = context.dataStore.edit {
        if (token == null) it.remove(tokenKey) else it[tokenKey] = token
        if (user == null) it.remove(userKey) else it[userKey] = user
    }

    suspend fun deviceId(): String {
        val existing = context.dataStore.data.first()[deviceKey]
        if (existing != null) return existing
        val id = "android-" + UUID.randomUUID().toString()
        context.dataStore.edit { it[deviceKey] = id }
        return id
    }

    fun deviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}"
}
