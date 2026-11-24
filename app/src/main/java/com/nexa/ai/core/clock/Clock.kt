package com.nexa.ai.core.clock

import javax.inject.Inject
/** Injectable time source so polling / expiry logic is deterministic in tests. */
interface Clock {
    fun nowMillis(): Long
}

class SystemClock @Inject constructor() : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
