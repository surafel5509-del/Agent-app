package com.nexa.ai.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nexa.ai.core.AppConstants
import com.nexa.ai.core.dispatcher.DispatcherProvider
import com.nexa.ai.domain.model.ThemeMode
import androidx.datastore.preferences.core.MutablePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "nexa_settings",
)

/** Everything the app remembers between launches except secrets. */
data class AppPreferences(
    val apiUrl: String = AppConstants.DEFAULT_API_URL,
    val username: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val selectedAgentId: String? = null,
    val selectedModelProviderId: String? = null,
    val selectedModelId: String? = null,
    val selectedModelName: String? = null,
    val activeSessionId: String? = null,
    /** Raw gateway payloads cached so agent/model pickers work offline. */
    val agentsRaw: String? = null,
    val providersRaw: String? = null,
) {
    val hasSelectedModel: Boolean
        get() = !selectedModelProviderId.isNullOrBlank() && !selectedModelId.isNullOrBlank()
}

@Singleton
class UserPreferencesDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) {

    private object Keys {
        val API_URL = stringPreferencesKey("api_url")
        val USERNAME = stringPreferencesKey("username")
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = stringPreferencesKey("dynamic_color")
        val SELECTED_AGENT = stringPreferencesKey("selected_agent")
        val MODEL_PROVIDER = stringPreferencesKey("selected_model_provider")
        val MODEL_ID = stringPreferencesKey("selected_model_id")
        val MODEL_NAME = stringPreferencesKey("selected_model_name")
        val ACTIVE_SESSION = stringPreferencesKey("active_session_id")
        val AGENTS_RAW = stringPreferencesKey("catalog_agents_raw")
        val PROVIDERS_RAW = stringPreferencesKey("catalog_providers_raw")
    }

    val preferences: Flow<AppPreferences> = context.settingsDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it.toAppPreferences() }

    suspend fun current(): AppPreferences = withContext(dispatchers.io) { preferences.first() }

    suspend fun setApiUrl(value: String) = edit { it[Keys.API_URL] = value }

    suspend fun setUsername(value: String?) = edit { prefs ->
        if (value.isNullOrBlank()) prefs.remove(Keys.USERNAME) else prefs[Keys.USERNAME] = value
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.DYNAMIC_COLOR] = enabled.toString() }

    suspend fun setSelectedAgent(agentId: String?) = edit { prefs ->
        if (agentId.isNullOrBlank()) prefs.remove(Keys.SELECTED_AGENT) else prefs[Keys.SELECTED_AGENT] = agentId
    }

    suspend fun setSelectedModel(providerId: String?, modelId: String?, modelName: String?) = edit { prefs ->
        if (providerId.isNullOrBlank() || modelId.isNullOrBlank()) {
            prefs.remove(Keys.MODEL_PROVIDER)
            prefs.remove(Keys.MODEL_ID)
            prefs.remove(Keys.MODEL_NAME)
        } else {
            prefs[Keys.MODEL_PROVIDER] = providerId
            prefs[Keys.MODEL_ID] = modelId
            prefs[Keys.MODEL_NAME] = modelName.orEmpty()
        }
    }

    suspend fun setActiveSession(sessionId: String?) = edit { prefs ->
        if (sessionId.isNullOrBlank()) prefs.remove(Keys.ACTIVE_SESSION) else prefs[Keys.ACTIVE_SESSION] = sessionId
    }

    suspend fun setCatalog(agentsRaw: String?, providersRaw: String?) = edit { prefs ->
        if (agentsRaw.isNullOrBlank()) prefs.remove(Keys.AGENTS_RAW) else prefs[Keys.AGENTS_RAW] = agentsRaw
        if (providersRaw.isNullOrBlank()) prefs.remove(Keys.PROVIDERS_RAW) else prefs[Keys.PROVIDERS_RAW] = providersRaw
    }

    suspend fun clearAll() = edit { it.clear() }

    private suspend fun edit(block: suspend (MutablePreferences) -> Unit) {
        withContext(dispatchers.io) {
            context.settingsDataStore.edit(block)
        }
    }

    private fun Preferences.toAppPreferences(): AppPreferences = AppPreferences(
        apiUrl = this[Keys.API_URL] ?: AppConstants.DEFAULT_API_URL,
        username = this[Keys.USERNAME],
        themeMode = this[Keys.THEME]?.let { stored -> runCatching { ThemeMode.valueOf(stored) }.getOrNull() }
            ?: ThemeMode.SYSTEM,
        dynamicColor = this[Keys.DYNAMIC_COLOR]?.toBooleanStrictOrNull() ?: true,
        selectedAgentId = this[Keys.SELECTED_AGENT],
        selectedModelProviderId = this[Keys.MODEL_PROVIDER],
        selectedModelId = this[Keys.MODEL_ID],
        selectedModelName = this[Keys.MODEL_NAME],
        activeSessionId = this[Keys.ACTIVE_SESSION],
        agentsRaw = this[Keys.AGENTS_RAW],
        providersRaw = this[Keys.PROVIDERS_RAW],
    )
}
