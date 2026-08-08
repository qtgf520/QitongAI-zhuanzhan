package com.qtwl.YitongAIzhuanzhan

import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

internal object Utf8RequestBody {
    private const val MAX_JSON_BODY_BYTES = 4 * 1024 * 1024

    fun readExact(inputStream: InputStream, contentLength: Long): String {
        require(contentLength >= 0L) { "Negative Content-Length" }
        require(contentLength <= MAX_JSON_BODY_BYTES.toLong()) {
            "JSON request body is too large"
        }

        val expected = contentLength.toInt()
        val bytes = ByteArray(expected)
        var offset = 0
        while (offset < expected) {
            val read = inputStream.read(bytes, offset, expected - offset)
            if (read < 0) {
                throw EOFException("Request body ended after $offset of $expected bytes")
            }
            if (read == 0) continue
            offset += read
        }
        return decodeStrict(bytes)
    }

    fun decodeStrict(bytes: ByteArray): String {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return decoder.decode(ByteBuffer.wrap(bytes)).toString()
    }

    fun validateLegacyDecodedBody(body: String): String {
        require('\uFFFD' !in body) {
            "Request body contains invalid Unicode replacement characters; send JSON as UTF-8"
        }
        return body
    }
}
