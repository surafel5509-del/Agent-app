package com.nexa.ai.data.repository

import com.nexa.ai.core.dispatcher.DispatcherProvider
import com.nexa.ai.core.error.AppError
import com.nexa.ai.core.error.toAppError
import com.nexa.ai.core.result.AppResult
import com.nexa.ai.data.local.prefs.UserPreferencesDataSource
import com.nexa.ai.data.remote.api.NexaApi
import com.nexa.ai.data.remote.parser.RemoteParser
import com.nexa.ai.di.ApplicationScope
import com.nexa.ai.domain.model.AgentInfo
import com.nexa.ai.domain.model.ModelInfo
import com.nexa.ai.domain.model.ProviderInfo
import com.nexa.ai.domain.repository.AgentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caches the agent/provider catalog in memory and in DataStore (as the raw gateway payload)
 * so the pickers still work offline or right after a cold start.
 */
@Singleton
class AgentRepositoryImpl @Inject constructor(
    private val api: NexaApi,
    private val parser: RemoteParser,
    private val prefs: UserPreferencesDataSource,
    private val dispatchers: DispatcherProvider,
    @ApplicationScope appScope: CoroutineScope,
) : AgentRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private val _agents = MutableStateFlow<List<AgentInfo>>(emptyList())
    private val _models = MutableStateFlow<List<ModelInfo>>(emptyList())
    private val _providers = MutableStateFlow<List<ProviderInfo>>(emptyList())

    override val agents: Flow<List<AgentInfo>> = _agents.asStateFlow()
    override val models: Flow<List<ModelInfo>> = _models.asStateFlow()
    override val providers: Flow<List<ProviderInfo>> = _providers.asStateFlow()

    init {
        appScope.launch {
            val cached = prefs.current()
            cached.agentsRaw?.let { raw -> _agents.value = parseAgents(raw) }
            cached.providersRaw?.let { raw -> applyProviders(parseProviders(raw)) }
        }
    }

    override suspend fun currentAgents(): List<AgentInfo> = _agents.value

    override suspend fun currentModels(): List<ModelInfo> = _models.value

    override suspend fun refresh(): AppResult<Unit> = withContext(dispatchers.io) {
        val agentsResponse = try {
            api.agents()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            return@withContext AppResult.Failure(throwable.toAppError())
        }
        if (!agentsResponse.isSuccessful) {
            return@withContext AppResult.Failure(
                AppError.fromHttp(agentsResponse.code(), agentsResponse.errorBody()?.string()?.take(200)),
            )
        }

        val agentsBody = agentsResponse.body()
        val parsedAgents = agentsBody?.let { parser.parseAgents(it) }.orEmpty()
        _agents.value = parsedAgents

        // Providers are optional: a 404 here must never block the chat experience.
        val providersResponse = try {
            api.providers()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            null
        }
        val parsedProviders = providersResponse
            ?.takeIf { it.isSuccessful }
            ?.body()
            ?.let { parser.parseProviders(it) }
            .orEmpty()
        applyProviders(parsedProviders)

        prefs.setCatalog(
            agentsRaw = agentsBody?.toString(),
            providersRaw = providersResponse?.takeIf { it.isSuccessful }?.body()?.toString(),
        )

        AppResult.Success(Unit)
    }

    private fun applyProviders(providers: List<ProviderInfo>) {
        _providers.value = providers
        _models.value = providers.flatMap { provider ->
            provider.models.map { model -> model.copy(providerName = provider.displayName) }
        }
    }

    private fun parseAgents(raw: String): List<AgentInfo> = try {
        parser.parseAgents(json.parseToJsonElement(raw))
    } catch (exception: Exception) {
        emptyList()
    }

    private fun parseProviders(raw: String): List<ProviderInfo> = try {
        parser.parseProviders(json.parseToJsonElement(raw))
    } catch (exception: Exception) {
        emptyList()
    }
}
