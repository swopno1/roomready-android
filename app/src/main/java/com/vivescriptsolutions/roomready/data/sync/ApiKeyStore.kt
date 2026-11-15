package com.vivescriptsolutions.roomready.data.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Property API key storage. Production uses AndroidKeyStore-backed
 * encrypted preferences; the key is never logged, never hardcoded, and
 * wiped on 401. Tests use [InMemoryApiKeyStore].
 */
interface ApiKeyStore {
  fun getKey(): String?

  fun hasKey(): Boolean

  fun saveKey(raw: String)

  fun clear()
}

class KeystoreApiKeyStore(context: Context) : ApiKeyStore {
  private val prefs: SharedPreferences by lazy {
    val masterKey =
      MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    EncryptedSharedPreferences.create(
      context.applicationContext,
      "pms_api_keys",
      masterKey,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
  }

  override fun getKey(): String? = prefs.getString(KEY_PROPERTY_API_KEY, null)

  override fun hasKey(): Boolean = !getKey().isNullOrBlank()

  override fun saveKey(raw: String) {
    prefs.edit { putString(KEY_PROPERTY_API_KEY, raw.trim()) }
  }

  override fun clear() {
    prefs.edit { remove(KEY_PROPERTY_API_KEY) }
  }

  private companion object {
    const val KEY_PROPERTY_API_KEY = "property_api_key"
  }
}

class InMemoryApiKeyStore : ApiKeyStore {
  private var key: String? = null

  override fun getKey(): String? = key

  override fun hasKey(): Boolean = !key.isNullOrBlank()

  override fun saveKey(raw: String) {
    key = raw.trim()
  }

  override fun clear() {
    key = null
  }
}
