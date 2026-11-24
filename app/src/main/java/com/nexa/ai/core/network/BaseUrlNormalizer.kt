package com.nexa.ai.core.network

import com.nexa.ai.core.AppConstants
import java.net.URI
import java.util.Locale

/**
 * Normalises and validates the user-provided gateway URL.
 *
 * Rules:
 *  - a blank value falls back to the bundled default;
 *  - a missing scheme is assumed to be https (http is only allowed for local emulators);
 *  - HTTPS is mandatory for anything that is not loopback / the Android emulator host;
 *  - a bare origin gets the `/v1` suffix appended, because every endpoint is relative to it;
 *  - trailing slashes are stripped so path joining is predictable.
 */
object BaseUrlNormalizer {

    sealed interface Validation {
        data class Valid(val url: String) : Validation
        data class Invalid(val reason: Reason) : Validation
    }

    enum class Reason { BLANK_HOST, UNSUPPORTED_SCHEME, HTTPS_REQUIRED, MALFORMED }

    private val localHosts = setOf("localhost", "127.0.0.1", "10.0.2.2", "0.0.0.0", "::1")

    fun normalize(input: String): String {
        val candidate = input.trim()
        if (candidate.isEmpty()) return AppConstants.DEFAULT_API_URL

        val withScheme = when {
            candidate.startsWith("http://", ignoreCase = true) ||
                candidate.startsWith("https://", ignoreCase = true) -> candidate
            else -> "https://$candidate"
        }
        return try {
            val uri = URI(withScheme)
            val host = uri.host?.lowercase(Locale.ROOT) ?: return withScheme.trimEnd('/')
            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: "https"
            val port = if (uri.port > 0) ":${uri.port}" else ""
            val path = uri.rawPath.orEmpty().trimEnd('/')
            val suffix = if (path.isEmpty()) "/v1" else path
            "$scheme://$host$port$suffix"
        } catch (exception: Exception) {
            withScheme.trimEnd('/')
        }
    }

    fun validate(input: String): Validation {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return Validation.Valid(AppConstants.DEFAULT_API_URL)

        val withScheme = when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> "https://$trimmed"
        }

        val uri = try {
            URI(withScheme)
        } catch (exception: Exception) {
            return Validation.Invalid(Reason.MALFORMED)
        }

        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "https" && scheme != "http") return Validation.Invalid(Reason.UNSUPPORTED_SCHEME)

        val host = uri.host?.lowercase(Locale.ROOT)
        if (host.isNullOrBlank()) return Validation.Invalid(Reason.BLANK_HOST)

        if (scheme == "http" && host !in localHosts) return Validation.Invalid(Reason.HTTPS_REQUIRED)

        return Validation.Valid(normalize(trimmed))
    }
}
