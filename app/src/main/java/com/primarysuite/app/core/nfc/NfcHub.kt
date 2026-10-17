package com.primarysuite.app.core.nfc

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NfcState { AVAILABLE, DISABLED, UNAVAILABLE }

/** A tag the phone just detected. Reads and writes may block, so call them off the main thread. */
interface NfcTag {
    fun readText(): NfcScanResult
    fun writeText(text: String): NfcWriteResult
}

/**
 * App-wide NFC state and tag routing. The activity owns the NFC adapter and
 * reports here; screens that use NFC collect [state] and [tags]. Tags tapped
 * while no screen is listening are dropped.
 */
class NfcHub {
    private val _state = MutableStateFlow(NfcState.UNAVAILABLE)
    val state: StateFlow<NfcState> = _state.asStateFlow()

    private val _tags = MutableSharedFlow<NfcTag>(extraBufferCapacity = 1)
    val tags: SharedFlow<NfcTag> = _tags.asSharedFlow()

    fun setState(state: NfcState) {
        _state.value = state
    }

    fun dispatch(tag: NfcTag) {
        _tags.tryEmit(tag)
    }
}
