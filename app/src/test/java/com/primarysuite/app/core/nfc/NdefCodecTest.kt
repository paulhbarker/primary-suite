package com.primarysuite.app.core.nfc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NdefCodecTest {

    @Test
    fun `decodes a UTF-8 text record`() {
        val payload = byteArrayOf(2) + "en".toByteArray() + "42".toByteArray()
        assertEquals("42", NdefCodec.decodeText(payload))
    }

    @Test
    fun `decodes a UTF-16 text record`() {
        val payload = byteArrayOf((0x80 or 2).toByte()) + "en".toByteArray() + "17".toByteArray(Charsets.UTF_16)
        assertEquals("17", NdefCodec.decodeText(payload))
    }

    @Test
    fun `handles longer language codes`() {
        val payload = byteArrayOf(5) + "en-US".toByteArray() + "99".toByteArray()
        assertEquals("99", NdefCodec.decodeText(payload))
    }

    @Test
    fun `empty text is allowed`() {
        assertEquals("", NdefCodec.decodeText(byteArrayOf(2) + "en".toByteArray()))
    }

    @Test
    fun `rejects malformed payloads`() {
        assertTrue(runCatching { NdefCodec.decodeText(byteArrayOf()) }.isFailure)
        assertTrue(runCatching { NdefCodec.decodeText(byteArrayOf(9, 'e'.code.toByte())) }.isFailure)
    }

    @Test
    fun `encodes what it decodes`() {
        val payload = NdefCodec.encodeText("100")
        assertArrayEquals(byteArrayOf(2) + "en".toByteArray() + "100".toByteArray(), payload)
        assertEquals("100", NdefCodec.decodeText(payload))
    }
}
