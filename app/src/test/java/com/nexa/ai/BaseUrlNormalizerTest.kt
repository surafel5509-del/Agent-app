package com.nexa.ai

import com.nexa.ai.core.AppConstants
import com.nexa.ai.core.network.BaseUrlNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseUrlNormalizerTest {

    @Test
    fun `blank input falls back to the bundled gateway`() {
        assertEquals(AppConstants.DEFAULT_API_URL, BaseUrlNormalizer.normalize("   "))
    }

    @Test
    fun `bare origin gets the v1 prefix`() {
        assertEquals(
            "https://opencode-production-ef5d07d3.up.railway.app/v1",
            BaseUrlNormalizer.normalize("opencode-production-ef5d07d3.up.railway.app"),
        )
    }

    @Test
    fun `trailing slashes are stripped and https is assumed`() {
        assertEquals(
            "https://example.com/v1",
            BaseUrlNormalizer.normalize(" example.com/v1/// "),
        )
    }

    @Test
    fun `custom port is preserved`() {
        assertEquals("https://example.com:8443/v1", BaseUrlNormalizer.normalize("example.com:8443/v1"))
    }

    @Test
    fun `path other than v1 is kept`() {
        assertEquals("https://example.com/api/opencode", BaseUrlNormalizer.normalize("example.com/api/opencode"))
    }

    @Test
    fun `http is rejected for remote hosts`() {
        val result = BaseUrlNormalizer.validate("http://example.com/v1")
        assertTrue(result is BaseUrlNormalizer.Validation.Invalid)
        assertEquals(
            BaseUrlNormalizer.Reason.HTTPS_REQUIRED,
            (result as BaseUrlNormalizer.Validation.Invalid).reason,
        )
    }

    @Test
    fun `http is allowed for emulator and loopback development`() {
        assertTrue(BaseUrlNormalizer.validate("http://10.0.2.2:3000/v1") is BaseUrlNormalizer.Validation.Valid)
        assertTrue(BaseUrlNormalizer.validate("http://localhost:3000/v1") is BaseUrlNormalizer.Validation.Valid)
    }

    @Test
    fun `malformed url is reported as invalid`() {
        val result = BaseUrlNormalizer.validate("https://")
        assertTrue(result is BaseUrlNormalizer.Validation.Invalid)
    }
}
