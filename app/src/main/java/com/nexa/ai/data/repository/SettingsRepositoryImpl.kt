package com.nexa.ai.data.repository

import com.nexa.ai.core.network.BaseUrlNormalizer
import com.nexa.ai.data.local.prefs.AppPreferences
import com.nexa.ai.data.local.prefs.UserPreferencesDataSource
import com.nexa.ai.domain.model.ModelInfo
import com.nexa.ai.domain.model.ThemeMode
import com.nexa.ai.domain.repository.SettingsRepository
import com.nexa.ai.domain.repository.SettingsSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val prefs: UserPreferencesDataSource,
) : SettingsRepository {

    override val preferences: Flow<SettingsSnapshot> = prefs.preferences.map { it.toSnapshot() }

    override val activeSessionId: Flow<String?> =
        preferences.map { it.activeSessionId }.distinctUntilChanged()

    override val selectedAgentId: Flow<String?> =
        preferences.map { it.selectedAgentId }.distinctUntilChanged()

    override val selectedModel: Flow<ModelInfo?> =
        preferences.map { it.selectedModel }.distinctUntilChanged()

    override val themeMode: Flow<ThemeMode> =
        preferences.map { it.themeMode }.distinctUntilChanged()

    override val isDynamicColorEnabled: Flow<Boolean> =
        preferences.map { it.dynamicColor }.distinctUntilChanged()

    override val apiUrl: Flow<String> = preferences.map { it.apiUrl }.distinctUntilChanged()

    override val username: Flow<String?> = preferences.map { it.username }.distinctUntilChanged()

    override suspend fun setApiUrl(url: String) {
        prefs.setApiUrl(BaseUrlNormalizer.normalize(url))
    }

    override suspend fun setUsername(username: String?) {
        prefs.setUsername(username?.trim()?.ifBlank { null })
    }

    override suspend fun setThemeMode(mode: ThemeMode) = prefs.setThemeMode(mode)

    override suspend fun setDynamicColor(enabled: Boolean) = prefs.setDynamicColor(enabled)

    override suspend fun selectAgent(agentId: String?) = prefs.setSelectedAgent(agentId)

    override suspend fun selectModel(model: ModelInfo?) = prefs.setSelectedModel(
        providerId = model?.providerId,
        modelId = model?.id,
        modelName = model?.name,
    )

    override suspend fun selectSession(sessionId: String?) = prefs.setActiveSession(sessionId)

    override suspend fun clearPreferences() = prefs.clearAll()
}

private fun AppPreferences.toSnapshot(): SettingsSnapshot = SettingsSnapshot(
    apiUrl = apiUrl,
    username = username,
    themeMode = themeMode,
    dynamicColor = dynamicColor,
    selectedAgentId = selectedAgentId,
    selectedModel = if (hasSelectedModel) {
        ModelInfo(
            providerId = selectedModelProviderId.orEmpty(),
            id = selectedModelId.orEmpty(),
            name = selectedModelName ?: selectedModelId.orEmpty(),
        )
    } else {
        null
    },
    activeSessionId = activeSessionId,
)
