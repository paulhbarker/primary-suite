package com.primarysuite.app.core.nfc

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import android.util.Log

/**
 * Stateless helper that encapsulates low-level NFC read/write operations,
 * keeping the Activity focused on lifecycle and UI concerns.
 */
object NfcHelper {

    private const val TAG = "NfcHelper"

    fun writeToTag(tag: Tag, dataToWrite: String): NfcWriteResult {
        val textRecord = NdefRecord.createTextRecord("en", dataToWrite)
        val ndefMessage = NdefMessage(arrayOf(textRecord))

        return try {
            val ndef = Ndef.get(tag)
                ?: return NfcWriteResult(success = false, error = "Tag is not NDEF formatted.")

            ndef.connect()
            ndef.use {
                if (it.maxSize < ndefMessage.toByteArray().size) {
                    NfcWriteResult(success = false, error = "Tag too small.")
                } else {
                    it.writeNdefMessage(ndefMessage)
                    NfcWriteResult(success = true)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to NFC", e)
            NfcWriteResult(success = false, error = e.message ?: "Exception during write.")
        }
    }

    fun readFromIntent(intent: Intent): NfcScanResult {
        val rawMessages: Array<NdefMessage>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)?.map { it as NdefMessage }?.toTypedArray()
        }

        if (rawMessages.isNullOrEmpty()) {
            return NfcScanResult(success = false, error = "No NDEF messages found on tag.")
        }

        return try {
            val text = NdefCodec.decodeText(rawMessages[0].records[0].payload)
            NfcScanResult(success = true, data = text)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing NDEF data", e)
            NfcScanResult(success = false, error = "Malformed NDEF Data: ${e.message}")
        }
    }
}

data class NfcWriteResult(val success: Boolean, val error: String? = null)

data class NfcScanResult(val success: Boolean, val data: String? = null, val error: String? = null)
