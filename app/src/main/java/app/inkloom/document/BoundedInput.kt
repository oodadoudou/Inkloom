package app.inkloom.document

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Works on every supported Android version and detects trailing data beyond the limit. */
internal fun InputStream.readBoundedBytes(limit: Int, message: String): ByteArray {
    require(limit >= 0)
    val result = ByteArrayOutputStream(minOf(limit, 8192))
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer, 0, minOf(buffer.size, limit - result.size() + 1))
        if (count < 0) return result.toByteArray()
        if (count == 0) {
            val byte = read()
            if (byte < 0) return result.toByteArray()
            require(result.size() < limit) { message }
            result.write(byte)
        } else {
            require(count <= limit - result.size()) { message }
            result.write(buffer, 0, count)
        }
    }
}
