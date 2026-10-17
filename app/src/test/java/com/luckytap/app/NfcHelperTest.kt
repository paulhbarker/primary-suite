package com.luckytap.app

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NfcHelperTest {

    private fun intentWith(vararg messages: NdefMessage): Intent =
        Intent(NfcAdapter.ACTION_NDEF_DISCOVERED).putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, messages)

    private fun textMessage(text: String) = NdefMessage(arrayOf(NdefRecord.createTextRecord("en", text)))

    @Test
    fun `reads the number from a text record`() {
        val result = NfcHelper.readFromIntent(intentWith(textMessage("42")))

        assertTrue(result.success)
        assertEquals("42", result.data)
        assertNull(result.error)
    }

    @Test
    fun `reads only the first record of the first message`() {
        val result = NfcHelper.readFromIntent(intentWith(textMessage("7"), textMessage("99")))

        assertEquals("7", result.data)
    }

    @Test
    fun `fails when the intent has no NDEF messages`() {
        val result = NfcHelper.readFromIntent(Intent(NfcAdapter.ACTION_TAG_DISCOVERED))

        assertFalse(result.success)
        assertNull(result.data)
        assertEquals("No NDEF messages found on tag.", result.error)
    }
}
