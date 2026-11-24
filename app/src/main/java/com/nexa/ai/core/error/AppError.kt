package com.nexa.ai.core.error

/**
 * App-wide error taxonomy.
 *
 * The UI never switches on raw exceptions: every failure is funneled through this sealed
 * hierarchy so that 401/403/429/5xx/network cases each get a precise, honest message and the
 * correct recovery action.
 */
enum class ErrorKind {
    OFFLINE,
    TIMEOUT,
    UNAUTHORIZED,
    FORBIDDEN,
    RATE_LIMITED,
    SERVER,
    CLIENT,
    VALIDATION,
    EMPTY_RESPONSE,
    UNKNOWN,
}

sealed interface AppError {
    val kind: ErrorKind
    val detail: String?
    val httpCode: Int?

    /** True when re-running the same request can plausibly succeed. */
    val isRetryable: Boolean
        get() = when (kind) {
            ErrorKind.OFFLINE, ErrorKind.TIMEOUT, ErrorKind.RATE_LIMITED, ErrorKind.SERVER -> true
            else -> false
        }

    data class Network(override val detail: String? = null) : AppError {
        override val kind = ErrorKind.OFFLINE
        override val httpCode: Int? = null
    }

    data class Timeout(override val detail: String? = null) : AppError {
        override val kind = ErrorKind.TIMEOUT
        override val httpCode: Int? = null
    }

    data class Unauthorized(override val detail: String? = null) : AppError {
        override val kind = ErrorKind.UNAUTHORIZED
        override val httpCode: Int? = 401
    }

    data class Forbidden(override val detail: String? = null) : AppError {
        override val kind = ErrorKind.FORBIDDEN
        override val httpCode: Int? = 403
    }

    data class RateLimited(override val detail: String? = null) : AppError {
        override val kind = ErrorKind.RATE_LIMITED
        override val httpCode: Int? = 429
    }

    data class Server(override val httpCode: Int, override val detail: String? = null) : AppError {
        override val kind = ErrorKind.SERVER
    }

    data class Client(override val httpCode: Int, override val detail: String? = null) : AppError {
        override val kind = ErrorKind.CLIENT
    }

    data class Validation(override val detail: String) : AppError {
        override val kind = ErrorKind.VALIDATION
        override val httpCode: Int? = null
    }

    data class EmptyResponse(override val detail: String? = null) : AppError {
        override val kind = ErrorKind.EMPTY_RESPONSE
        override val httpCode: Int? = null
    }

    data class Unknown(override val detail: String? = null, val cause: Throwable? = null) : AppError {
        override val kind = ErrorKind.UNKNOWN
        override val httpCode: Int? = null
    }

    companion object {
        /** Maps an HTTP status code to the most specific [AppError]. */
        fun fromHttp(code: Int, detail: String? = null): AppError = when (code) {
            401 -> AppError.Unauthorized(detail)
            403 -> AppError.Forbidden(detail)
            429 -> AppError.RateLimited(detail)
            in 500..599 -> AppError.Server(code, detail)
            else -> AppError.Client(code, detail)
        }
    }
}

/** Converts any throwable into an [AppError], preserving one that was already mapped. */
fun Throwable.toAppError(): AppError = when (this) {
    is AppError -> this
    is java.net.SocketTimeoutException -> AppError.Timeout(message)
    is java.net.UnknownHostException -> AppError.Network("DNS lookup failed")
    is java.net.ConnectException -> AppError.Network("Could not reach the server")
    is java.net.UnknownServiceException -> AppError.Network("HTTPS is required")
    is javax.net.ssl.SSLException -> AppError.Network("Secure connection failed")
    is java.io.IOException -> AppError.Network(message)
    is kotlinx.serialization.SerializationException -> AppError.Unknown("Unexpected response format", this)
    else -> AppError.Unknown(message, this)
}
