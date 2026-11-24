package com.nexa.ai

import com.nexa.ai.domain.polling.BackoffPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class BackoffPolicyTest {

    @Test
    fun `delays double until the cap is reached`() {
        assertEquals(1_000L, BackoffPolicy.delayFor(0, 1_000L, 15_000L))
        assertEquals(2_000L, BackoffPolicy.delayFor(1, 1_000L, 15_000L))
        assertEquals(4_000L, BackoffPolicy.delayFor(2, 1_000L, 15_000L))
        assertEquals(8_000L, BackoffPolicy.delayFor(3, 1_000L, 15_000L))
        assertEquals(15_000L, BackoffPolicy.delayFor(4, 1_000L, 15_000L))
        assertEquals(15_000L, BackoffPolicy.delayFor(9, 1_000L, 15_000L))
    }

    @Test
    fun `never overflows for very large attempt counts`() {
        assertEquals(15_000L, BackoffPolicy.delayFor(Int.MAX_VALUE, 1_000L, 15_000L))
        assertEquals(1_000L, BackoffPolicy.delayFor(-5, 1_000L, 15_000L))
    }
}
