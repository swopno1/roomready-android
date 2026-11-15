package com.vivescriptsolutions.roomready.data.preferences

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vivescriptsolutions.roomready.domain.AppLanguage
import com.vivescriptsolutions.roomready.domain.StaffRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.roomReadyDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "roomready_preferences")

data class UserSession(
  val role: StaffRole? = null,
  val staffName: String? = null,
  val staffRoster: List<String> = emptyList(),
) {
  val isOnboarded: Boolean
    get() = role != null && !staffName.isNullOrBlank()
}

data class PmsConnection(
  val isConfigured: Boolean = false,
  val baseUrl: String? = null,
)

class UserPreferencesRepository(private val context: Context) {
  private object Keys {
    val SELECTED_ROLE = stringPreferencesKey("selected_role")
    val SELECTED_STAFF_NAME = stringPreferencesKey("selected_staff_name")
    val CUSTOM_STAFF_NAMES = stringSetPreferencesKey("custom_staff_names")
    val PMS_CONFIGURED = booleanPreferencesKey("pms_configured")
    val PMS_BASE_URL = stringPreferencesKey("pms_base_url")
    val APP_LANGUAGE = stringPreferencesKey("app_language")
    val NOTIFICATIONS_ASKED = booleanPreferencesKey("notifications_asked")
    val STUCK_NOTIFIED_RUN_IDS = stringSetPreferencesKey("stuck_notified_run_ids")
    // Legacy pre-production key. Old installs stored a Demo/Live mode
    // string here; it is read once as a configured-state fallback so a
    // previously connected install keeps its snapshot on upgrade. Never
    // written by this build.
    val LEGACY_CONNECTION_MODE = stringPreferencesKey("connection_mode")
  }

  val sessionFlow: Flow<UserSession> =
    context.roomReadyDataStore.data.map { prefs ->
      val role =
        prefs[Keys.SELECTED_ROLE]?.let { raw ->
          runCatching { StaffRole.valueOf(raw) }.getOrNull()
        }
      UserSession(
        role = role,
        staffName = prefs[Keys.SELECTED_STAFF_NAME],
        staffRoster = prefs[Keys.CUSTOM_STAFF_NAMES].orEmpty().sorted(),
      )
    }

  suspend fun saveRoleAndStaff(role: StaffRole, staffName: String) {
    val trimmed = staffName.trim()
    context.roomReadyDataStore.edit { prefs ->
      prefs[Keys.SELECTED_ROLE] = role.name
      prefs[Keys.SELECTED_STAFF_NAME] = trimmed
      if (trimmed.isNotEmpty()) {
        val current = prefs[Keys.CUSTOM_STAFF_NAMES].orEmpty()
        prefs[Keys.CUSTOM_STAFF_NAMES] = current + trimmed
      }
    }
  }

  suspend fun setRole(role: StaffRole) {
    context.roomReadyDataStore.edit { prefs -> prefs[Keys.SELECTED_ROLE] = role.name }
  }

  suspend fun setStaffName(staffName: String) {
    val trimmed = staffName.trim()
    if (trimmed.isEmpty()) return
    context.roomReadyDataStore.edit { prefs ->
      prefs[Keys.SELECTED_STAFF_NAME] = trimmed
      val current = prefs[Keys.CUSTOM_STAFF_NAMES].orEmpty()
      prefs[Keys.CUSTOM_STAFF_NAMES] = current + trimmed
    }
  }

  fun observeConnection(): Flow<PmsConnection> =
    context.roomReadyDataStore.data.map { prefs ->
      val configured =
        prefs[Keys.PMS_CONFIGURED]
          ?: (prefs[Keys.LEGACY_CONNECTION_MODE] == "LIVE")
      PmsConnection(
        isConfigured = configured,
        baseUrl = prefs[Keys.PMS_BASE_URL]?.takeIf { it.isNotBlank() },
      )
    }

  suspend fun connection(): PmsConnection = observeConnection().first()

  suspend fun setConfigured(configured: Boolean) {
    context.roomReadyDataStore.edit { prefs -> prefs[Keys.PMS_CONFIGURED] = configured }
  }

  suspend fun setBaseUrl(baseUrl: String) {
    context.roomReadyDataStore.edit { prefs ->
      prefs[Keys.PMS_BASE_URL] = baseUrl.trim().trimEnd('/')
    }
  }

  suspend fun clearConnection() {
    context.roomReadyDataStore.edit { prefs ->
      prefs.remove(Keys.PMS_BASE_URL)
      prefs[Keys.PMS_CONFIGURED] = false
    }
  }

  fun observeAppLanguage(): Flow<AppLanguage> =
    context.roomReadyDataStore.data.map { prefs ->
      prefs[Keys.APP_LANGUAGE]?.let { raw ->
        runCatching { AppLanguage.valueOf(raw) }.getOrNull()
      } ?: AppLanguage.SYSTEM
    }

  suspend fun appLanguage(): AppLanguage = observeAppLanguage().first()

  suspend fun setAppLanguage(language: AppLanguage) {
    context.roomReadyDataStore.edit { prefs -> prefs[Keys.APP_LANGUAGE] = language.name }
  }

  suspend fun notificationsAsked(): Boolean =
    context.roomReadyDataStore.data.first()[Keys.NOTIFICATIONS_ASKED] ?: false

  suspend fun markNotificationsAsked() {
    context.roomReadyDataStore.edit { prefs -> prefs[Keys.NOTIFICATIONS_ASKED] = true }
  }

  suspend fun stuckNotifiedRunIds(): Set<Long> =
    context.roomReadyDataStore.data.first()[Keys.STUCK_NOTIFIED_RUN_IDS]
      .orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

  suspend fun setStuckNotifiedRunIds(ids: Set<Long>) {
    context.roomReadyDataStore.edit { prefs ->
      prefs[Keys.STUCK_NOTIFIED_RUN_IDS] = ids.map { it.toString() }.toSet()
    }
  }

  /** Testing only: clears every stored preference for isolation. */
  @VisibleForTesting
  suspend fun clearForTesting() {
    context.roomReadyDataStore.edit { it.clear() }
  }
}
