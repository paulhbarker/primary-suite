package com.primarysuite.app.core.nfc

/**
 * NDEF Text record payloads (NFC Forum RTD-Text), without any Android types.
 *
 * Layout: one status byte, then the language code, then the text.
 * Status bit 7 is the encoding (0 = UTF-8, 1 = UTF-16), bits 0-5 are the
 * language code length.
 */
object NdefCodec {

    private const val UTF16_FLAG = 0x80
    private const val LANGUAGE_LENGTH_MASK = 0x3F

    /** Returns the text in a Text record payload, or throws [IllegalArgumentException] if malformed. */
    fun decodeText(payload: ByteArray): String {
        require(payload.isNotEmpty()) { "Empty payload" }
        val status = payload[0].toInt()
        val languageLength = status and LANGUAGE_LENGTH_MASK
        val textStart = 1 + languageLength
        require(textStart <= payload.size) { "Language code length $languageLength exceeds payload" }
        val charset = if (status and UTF16_FLAG == 0) Charsets.UTF_8 else Charsets.UTF_16
        return String(payload, textStart, payload.size - textStart, charset)
    }

    /** Builds a UTF-8 Text record payload. */
    fun encodeText(text: String, languageCode: String = "en"): ByteArray {
        val language = languageCode.toByteArray(Charsets.US_ASCII)
        require(language.size <= LANGUAGE_LENGTH_MASK) { "Language code too long" }
        return byteArrayOf(language.size.toByte()) + language + text.toByteArray(Charsets.UTF_8)
    }
}
