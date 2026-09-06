package org.tinitalk.admin.io

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

internal class InputTooLargeException(val limitBytes: Int) : IOException("Input exceeds $limitBytes bytes")

// The caller owns the stream and must close it, including when the limit is exceeded.
internal fun InputStream.readBytesLimited(maxBytes: Int): ByteArray {
    require(maxBytes >= 0)
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (output.size() < maxBytes) {
        val count = read(buffer, 0, minOf(buffer.size, maxBytes - output.size()))
        if (count == -1) return output.toByteArray()
        output.write(buffer, 0, count)
    }
    // Read one extra byte to distinguish an exact fit from an oversized input.
    if (read() != -1) throw InputTooLargeException(maxBytes)
    return output.toByteArray()
}
