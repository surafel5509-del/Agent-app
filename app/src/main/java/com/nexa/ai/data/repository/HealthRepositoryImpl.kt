package com.nexa.ai.data.repository

import com.nexa.ai.core.clock.Clock
import com.nexa.ai.core.dispatcher.DispatcherProvider
import com.nexa.ai.core.error.AppError
import com.nexa.ai.core.error.toAppError
import com.nexa.ai.core.result.AppResult
import com.nexa.ai.data.remote.api.NexaApi
import com.nexa.ai.data.remote.parser.RemoteParser
import com.nexa.ai.domain.model.HealthInfo
import com.nexa.ai.domain.repository.HealthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthRepositoryImpl @Inject constructor(
    private val api: NexaApi,
    private val parser: RemoteParser,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : HealthRepository {

    override suspend fun check(): AppResult<HealthInfo> = withContext(dispatchers.io) {
        val startedAt = clock.nowMillis()
        val response = try {
            api.health()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            return@withContext AppResult.Failure(throwable.toAppError())
        }
        if (!response.isSuccessful) {
            return@withContext AppResult.Failure(
                AppError.fromHttp(response.code(), response.errorBody()?.string()?.take(200)),
            )
        }
        val body = response.body()
            ?: return@withContext AppResult.Failure(AppError.EmptyResponse("Empty health response"))
        AppResult.Success(
            parser.parseHealth(body).copy(latencyMillis = clock.nowMillis() - startedAt),
        )
    }
}
