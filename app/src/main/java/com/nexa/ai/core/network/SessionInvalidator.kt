package com.nexa.ai.core.network

import com.nexa.ai.core.clock.Clock
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single channel through which "this token is no longer valid" travels to the auth layer.
 *
 * Keeping it separate from the interceptor avoids a dependency cycle: the interceptor only
 * knows how to report, the repository decides what to do (clear the token, return to login).
 */
@Singleton
class SessionInvalidator @Inject constructor(
    private val clock: Clock,
) {

    private val _events = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    @Volatile
    private var lastEmitAt: Long = 0L

    fun invalidate() {
        val now = clock.nowMillis()
        // Several parallel requests can 401 at once; collapse them into a single event.
        if (now - lastEmitAt < THROTTLE_MILLIS) return
        lastEmitAt = now
        _events.tryEmit(Unit)
    }

    private companion object {
        const val THROTTLE_MILLIS = 2_000L
    }
}
