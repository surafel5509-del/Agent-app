package com.nexa.ai.core.network

import com.nexa.ai.core.AppConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the gateway base URL that is currently in effect.
 *
 * Retrofit's base URL is fixed at construction time, so requests are rewritten by
 * [BaseUrlInterceptor] using this holder. It is initialised from the bundled default and
 * replaced as soon as the persisted preference (or the login screen) provides a value.
 */
@Singleton
class BaseUrlHolder @Inject constructor() {

    private val _url = MutableStateFlow(normalize(AppConstants.DEFAULT_API_URL))
    val url: StateFlow<String> = _url

    @Volatile
    private var cachedHttpUrl: HttpUrl? = _url.value.toHttpUrlOrNull()

    val current: HttpUrl
        get() = cachedHttpUrl ?: AppConstants.DEFAULT_API_URL.toHttpUrlOrNull()
            ?: error("Default gateway URL is not parseable")

    fun update(rawUrl: String) {
        val normalized = normalize(rawUrl)
        val parsed = normalized.toHttpUrlOrNull() ?: return
        cachedHttpUrl = parsed
        _url.value = normalized
    }

    private fun normalize(rawUrl: String): String =
        BaseUrlNormalizer.normalize(rawUrl).ifBlank { AppConstants.DEFAULT_API_URL }
}
