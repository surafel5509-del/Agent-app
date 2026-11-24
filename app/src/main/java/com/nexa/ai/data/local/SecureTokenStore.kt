package com.nexa.ai.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nexa.ai.core.clock.Clock
import com.nexa.ai.core.crypto.CryptoFactory
import com.nexa.ai.core.crypto.EncryptedBlob
import com.nexa.ai.core.crypto.TokenCipher
import com.nexa.ai.core.dispatcher.DispatcherProvider
import com.nexa.ai.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.secureTokenDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "nexa_secure_token",
)

/**
 * Keystore-backed token storage.
 *
 * The access token is encrypted with AES/GCM using a non-exportable Android Keystore key.
 * DataStore only ever holds ciphertext + IV; the password is never persisted anywhere.
 */
@Singleton
class SecureTokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cipher: TokenCipher,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
    @ApplicationScope appScope: CoroutineScope,
) : TokenStore {

    private val _token = MutableStateFlow<StoredToken?>(null)
    override val token: StateFlow<StoredToken?> = _token.asStateFlow()

    private val loaded = CompletableDeferred<Unit>()

    init {
        appScope.launch {
            try {
                restore()
            } finally {
                loaded.complete(Unit)
            }
        }
    }

    override suspend fun awaitLoaded() {
        loaded.await()
    }

    override fun currentOrNull(): StoredToken? {
        val current = _token.value ?: return null
        return if (current.isExpired(clock.nowMillis())) null else current
    }

    override suspend fun save(token: StoredToken) {
        withContext(dispatchers.io) {
            val encrypted = cipher.encrypt(token.accessToken)
            context.secureTokenDataStore.edit { preferences ->
                preferences[Keys.CIPHERTEXT] = CryptoFactory.base64Encode(encrypted.ciphertext)
                preferences[Keys.IV] = CryptoFactory.base64Encode(encrypted.iv)
                preferences[Keys.TOKEN_TYPE] = token.tokenType
                preferences[Keys.EXPIRES_AT] = token.expiresAt
                preferences[Keys.USERNAME] = token.username.orEmpty()
            }
        }
        _token.value = token
    }

    override suspend fun clear() {
        withContext(dispatchers.io) {
            context.secureTokenDataStore.edit { preferences -> preferences.clear() }
        }
        _token.value = null
    }

    private suspend fun restore() {
        val restored = withContext(dispatchers.io) { readFromDisk() }
        if (restored != null && !restored.isExpired(clock.nowMillis())) {
            _token.value = restored
        } else if (restored != null) {
            clear()
        }
    }

    private suspend fun readFromDisk(): StoredToken? {
        val snapshot = try {
            context.secureTokenDataStore.data.first()
        } catch (exception: Exception) {
            return null
        }
        val ciphertext = snapshot[Keys.CIPHERTEXT] ?: return null
        val iv = snapshot[Keys.IV] ?: return null
        return try {
            val decrypted = cipher.decrypt(
                EncryptedBlob(
                    ciphertext = CryptoFactory.base64Decode(ciphertext),
                    iv = CryptoFactory.base64Decode(iv),
                ),
            )
            StoredToken(
                accessToken = decrypted,
                tokenType = snapshot[Keys.TOKEN_TYPE] ?: "Bearer",
                expiresAt = snapshot[Keys.EXPIRES_AT] ?: 0L,
                username = snapshot[Keys.USERNAME]?.ifBlank { null },
            )
        } catch (exception: Exception) {
            // Keystore key rotated or ciphertext corrupt: the token is unrecoverable.
            // The user simply signs in again; nothing else is lost.
            null
        }
    }

    private object Keys {
        val CIPHERTEXT = stringPreferencesKey("token_ciphertext")
        val IV = stringPreferencesKey("token_iv")
        val TOKEN_TYPE = stringPreferencesKey("token_type")
        val EXPIRES_AT = longPreferencesKey("token_expires_at")
        val USERNAME = stringPreferencesKey("token_username")
    }
}
