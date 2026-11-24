package com.nexa.ai.core.network

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rewrites every outgoing request onto the configured gateway origin and path prefix.
 *
 * Retrofit is built against a placeholder URL; this interceptor replaces scheme, host, port
 * and mounts the endpoint path under the configured base path (e.g. `/v1`).
 */
@Singleton
class BaseUrlInterceptor @Inject constructor(
    private val baseUrlHolder: BaseUrlHolder,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val base = baseUrlHolder.current

        val mountedPath = joinPath(base.encodedPath, request.url.encodedPath)
        val rewritten = request.url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .encodedPath(mountedPath)
            .build()

        return chain.proceed(request.newBuilder().url(rewritten).build())
    }

    private fun joinPath(basePath: String, requestPath: String): String {
        val prefix = basePath.trimEnd('/')
        val relative = requestPath.trimStart('/')
        return if (prefix.isEmpty()) "/$relative" else "$prefix/$relative"
    }
}
