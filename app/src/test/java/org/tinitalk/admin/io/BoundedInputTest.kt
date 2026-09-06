package org.tinitalk.admin.io

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedInputTest {
    @Test
    fun acceptsEmptySmallerAndExactInputsAcrossBufferBoundary() {
        for (size in listOf(0, 8191, 8192, 8193)) {
            val bytes = ByteArray(size) { it.toByte() }
            assertArrayEquals(bytes, bytes.inputStream().use { it.readBytesLimited(8193) })
        }
        assertArrayEquals(byteArrayOf(), byteArrayOf().inputStream().use { it.readBytesLimited(0) })
    }

    @Test
    fun rejectsOneByteOverLimitWithoutConsumingTheRest() {
        for (limit in listOf(0, 8191, 8192, 8193)) {
            val input = ByteArray(10_000).inputStream()
            val error = assertThrows(InputTooLargeException::class.java) {
                input.use { it.readBytesLimited(limit) }
            }
            assertEquals(limit, error.limitBytes)
            assertEquals(10_000 - limit - 1, input.available())
        }
    }

    @Test
    fun ignoresAvailableAndHandlesShortReads() {
        val bytes = ByteArray(20) { it.toByte() }
        for (advertisedSize in listOf(0, Int.MAX_VALUE)) {
            val input = object : ByteArrayInputStream(bytes) {
                override fun available() = advertisedSize
                override fun read(buffer: ByteArray, offset: Int, length: Int) =
                    super.read(buffer, offset, minOf(length, 3))
            }
            assertArrayEquals(bytes, input.use { it.readBytesLimited(bytes.size) })
        }
    }

    @Test
    fun stopsEndlessInputAndClosesItOnFailure() {
        var bytesRead = 0
        var closed = false
        val input = object : InputStream() {
            override fun read(): Int {
                bytesRead++
                return 42
            }
            override fun close() { closed = true }
        }
        assertThrows(InputTooLargeException::class.java) {
            input.use { it.readBytesLimited(8193) }
        }
        assertEquals(8194, bytesRead)
        assertTrue(closed)
    }

    @Test
    fun propagatesReadFailureAndClosesInput() {
        var closed = false
        val failure = IOException("Synthetic read failure")
        val input = object : InputStream() {
            override fun read(): Int = throw failure
            override fun close() { closed = true }
        }
        assertEquals(failure, assertThrows(IOException::class.java) {
            input.use { it.readBytesLimited(10) }
        })
        assertTrue(closed)
    }
}
