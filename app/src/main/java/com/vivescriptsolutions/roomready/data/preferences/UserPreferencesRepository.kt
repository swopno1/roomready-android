package com.vivescriptsolutions.roomready.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vivescriptsolutions.roomready.domain.StaffRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.roomReadyDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "roomready_preferences")

data class UserSession(
  val role: StaffRole? = null,
  val staffName: String? = null,
  val isDemoMode: Boolean = true,
  val hasSeededDemo: Boolean = false,
  val staffRoster: List<String> = DEFAULT_STAFF_ROSTER,
) {
  val isOnboarded: Boolean
    get() = role != null && !staffName.isNullOrBlank()
}

val DEFAULT_STAFF_ROSTER: List<String> =
  listOf(
    "Amina Rahman",
    "Tariq Hasan",
    "Maya Devi",
    "Rafiq Islam",
    "Nadia Chowdhury",
    "Sajib Mia",
  )

class UserPreferencesRepository(private val context: Context) {
  private object Keys {
    val SELECTED_ROLE = stringPreferencesKey("selected_role")
    val SELECTED_STAFF_NAME = stringPreferencesKey("selected_staff_name")
    val IS_DEMO_MODE = booleanPreferencesKey("is_demo_mode")
    val HAS_SEEDED_DEMO = booleanPreferencesKey("has_seeded_demo")
    val CUSTOM_STAFF_NAMES = stringSetPreferencesKey("custom_staff_names")
  }

  val sessionFlow: Flow<UserSession> =
    context.roomReadyDataStore.data.map { prefs ->
      val role =
        prefs[Keys.SELECTED_ROLE]?.let { raw ->
          runCatching { StaffRole.valueOf(raw) }.getOrNull()
        }
      val customNames = prefs[Keys.CUSTOM_STAFF_NAMES].orEmpty().sorted()
      val mergedRoster = (DEFAULT_STAFF_ROSTER + customNames).distinct()
      UserSession(
        role = role,
        staffName = prefs[Keys.SELECTED_STAFF_NAME],
        isDemoMode = prefs[Keys.IS_DEMO_MODE] ?: true,
        hasSeededDemo = prefs[Keys.HAS_SEEDED_DEMO] ?: false,
        staffRoster = mergedRoster,
      )
    }

  suspend fun saveRoleAndStaff(role: StaffRole, staffName: String) {
    val trimmed = staffName.trim()
    context.roomReadyDataStore.edit { prefs ->
      prefs[Keys.SELECTED_ROLE] = role.name
      prefs[Keys.SELECTED_STAFF_NAME] = trimmed
      if (trimmed.isNotEmpty() && trimmed !in DEFAULT_STAFF_ROSTER) {
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
      if (trimmed !in DEFAULT_STAFF_ROSTER) {
        val current = prefs[Keys.CUSTOM_STAFF_NAMES].orEmpty()
        prefs[Keys.CUSTOM_STAFF_NAMES] = current + trimmed
      }
    }
  }

  suspend fun markDemoSeeded() {
    context.roomReadyDataStore.edit { prefs ->
      prefs[Keys.HAS_SEEDED_DEMO] = true
      prefs[Keys.IS_DEMO_MODE] = true
    }
  }
}
