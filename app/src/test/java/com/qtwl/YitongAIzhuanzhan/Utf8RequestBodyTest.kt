package com.qtwl.YitongAIzhuanzhan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.CharacterCodingException

class Utf8RequestBodyTest {
    @Test
    fun exactUtf8ReadPreservesChineseEmojiAndPunctuation() {
        val json = """{"model":"qtai-sj","messages":[{"role":"user","content":"你好，测试中文 🚀 — café"}]}"""
        val bytes = json.toByteArray(Charsets.UTF_8)

        val decoded = Utf8RequestBody.readExact(ByteArrayInputStream(bytes), bytes.size.toLong())

        assertEquals(json, decoded)
    }

    @Test
    fun strictDecoderRejectsMalformedUtf8InsteadOfProducingReplacementCharacters() {
        val malformed = byteArrayOf(0x7B, 0x22, 0x78, 0x22, 0x3A, 0x22, 0xE4.toByte(), 0x22, 0x7D)

        assertThrows(CharacterCodingException::class.java) {
            Utf8RequestBody.decodeStrict(malformed)
        }
    }

    @Test
    fun legacyFallbackRejectsAlreadyCorruptedReplacementCharacters() {
        assertThrows(IllegalArgumentException::class.java) {
            Utf8RequestBody.validateLegacyDecodedBody("{\"content\":\"���\"}")
        }
    }
}
