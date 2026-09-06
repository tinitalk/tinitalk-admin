package org.tinitalk.admin.server

import org.tinitalk.admin.io.InputTooLargeException
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TiniTalkHealthCheckerTest {
    private val checker = TiniTalkHealthChecker()
    private val response = """{"service":"tinitalk","status":"ok","api_version":4,"commit":"01234567","features":["video_1to1","single_device_session","webpush_v1","personal_contacts"]}"""
    private val limit = TiniTalkHealthChecker.MAX_HEALTH_RESPONSE_BYTES

    @Test
    fun readsCurrentServerFormatAndClosesInput() {
        val input = TrackedInput(response.toByteArray())
        assertEquals(TiniTalkHealthInfo(4, "01234567"), checker.readResponse(input))
        assertTrue(input.closed)
    }

    @Test
    fun acceptsExactLimitButRejectsOneExtraByteEvenAfterValidJson() {
        val body = response.padEnd(limit, ' ').toByteArray()
        assertEquals(TiniTalkHealthInfo(4, "01234567"), checker.readResponse(TrackedInput(body)))
        val oversized = TrackedInput(body + byteArrayOf(32))
        assertThrows(InputTooLargeException::class.java) { checker.readResponse(oversized) }
        assertTrue(oversized.closed)
    }

    @Test
    fun limitsUtf8BytesNotCharacterCount() {
        val body = """{"service":"tinitalk","status":"ok","extra":"${"я".repeat(limit / 2)}"}"""
        assertTrue(body.length < limit)
        val input = TrackedInput(body.toByteArray(Charsets.UTF_8))
        assertThrows(InputTooLargeException::class.java) { checker.readResponse(input) }
        assertTrue(input.closed)
    }

    @Test
    fun closesInputForInvalidJson() {
        val input = TrackedInput("not json".toByteArray())
        assertThrows(com.google.gson.JsonParseException::class.java) { checker.readResponse(input) }
        assertTrue(input.closed)
    }

    private class TrackedInput(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false
        override fun available(): Int = error("Do not trust the advertised size")
        override fun close() { closed = true }
    }
}
