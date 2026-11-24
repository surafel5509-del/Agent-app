package com.nexa.ai.core

/**
 * Central place for values that are not secrets.
 *
 * Nothing in this file (or anywhere in the repository) may contain a password, JWT,
 * API key or provider token. The only remotely sensitive value is the public gateway URL,
 * which is intentionally user-editable at runtime.
 */
object AppConstants {

    /** Public gateway URL. Prefilled on the login screen and editable in Settings. */
    const val DEFAULT_API_URL = "https://opencode-production-ef5d07d3.up.railway.app/v1"

    /** Retrofit needs a syntactically valid placeholder; it is rewritten per request. */
    const val RETROFIT_PLACEHOLDER_URL = "https://nexa.invalid/"

    const val CONNECT_TIMEOUT_SECONDS = 20L
    const val READ_TIMEOUT_SECONDS = 45L
    const val WRITE_TIMEOUT_SECONDS = 45L

    const val MESSAGE_PAGE_SIZE = 50

    const val DEFAULT_AGENT = "build"

    /** Draft key used while the user is composing before a session exists. */
    const val NEW_DRAFT_KEY = "new"

    const val TOKEN_KEY_ALIAS = "nexa_ai_token_key"
}
