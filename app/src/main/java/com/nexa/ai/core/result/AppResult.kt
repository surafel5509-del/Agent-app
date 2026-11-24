package com.nexa.ai.core.result

import com.nexa.ai.core.error.AppError
import com.nexa.ai.core.error.toAppError
import kotlinx.coroutines.CancellationException

/**
 * Explicit result type used across the data/domain boundary.
 * Exceptions are reserved for programming errors; expected failures are values.
 */
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>

    val isSuccess: Boolean
        get() = this is Success

    val isFailure: Boolean
        get() = this is Failure

    fun getOrNull(): T? = (this as? Success)?.value

    fun errorOrNull(): AppError? = (this as? Failure)?.error
}

fun <T> AppResult<T>.getOrDefault(default: T): T = getOrNull() ?: default

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}

inline fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) block(value)
    return this
}

inline fun <T> AppResult<T>.onFailure(block: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) block(error)
    return this
}

/**
 * Runs [block], converting failures to [AppError]. Coroutine cancellation is always
 * rethrown so structured concurrency keeps working.
 */
suspend fun <T> runCatchingApp(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    AppResult.Failure(throwable.toAppError())
}
