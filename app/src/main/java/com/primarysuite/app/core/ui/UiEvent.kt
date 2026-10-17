package com.primarysuite.app.core.ui

/**
 * One-shot events a view model emits for the screen to show, such as toasts.
 * Keeps Toast and other framework calls out of view models.
 */
sealed class UiEvent {
    data class ShowToast(val message: String, val longDuration: Boolean = false) : UiEvent()
}
