package com.primarysuite.app.games.spinwheel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WheelSettingsFormTest {

    private val form = WheelSettingsForm.from(WheelSettings.DEFAULTS)

    @Test
    fun `shows the settings in seconds and round trips`() {
        assertEquals(WheelSettingsForm("8", "10", "10", 0.8), form)
        assertTrue(form.isValid)
        assertEquals(WheelSettings.DEFAULTS, form.toSettings())
        assertEquals(WheelSettings(7500, 8250, 1500, 0.8), form.copy(spinMin = "7.5", spinMax = "8.25", celebrate = "1.5").toSettings())
    }

    @Test
    fun `checks the server's ranges`() {
        assertEquals(WheelFieldProblem.OUT_OF_RANGE, form.copy(spinMin = "2.9").problems()[WheelField.SPIN_MIN])
        assertEquals(WheelFieldProblem.OUT_OF_RANGE, form.copy(spinMax = "15.1").problems()[WheelField.SPIN_MAX])
        assertEquals(WheelFieldProblem.OUT_OF_RANGE, form.copy(celebrate = "0.5").problems()[WheelField.CELEBRATE])
        assertEquals(WheelFieldProblem.OUT_OF_RANGE, form.copy(celebrate = "31").problems()[WheelField.CELEBRATE])
        assertTrue(form.copy(spinMin = "3", spinMax = "15", celebrate = "30").isValid)
        assertTrue(form.copy(spinMin = "4", spinMax = "4", celebrate = "1").isValid)
    }

    @Test
    fun `the longest spin can't be shorter than the shortest`() {
        val bad = form.copy(spinMin = "9", spinMax = "8")
        assertEquals(mapOf(WheelField.SPIN_MAX to WheelFieldProblem.MAX_BELOW_MIN), bad.problems())
        assertNull(bad.toSettings())
    }

    @Test
    fun `half typed numbers are not numbers`() {
        assertEquals(WheelFieldProblem.NOT_A_NUMBER, form.copy(spinMin = "").problems()[WheelField.SPIN_MIN])
        assertEquals(WheelFieldProblem.NOT_A_NUMBER, form.copy(celebrate = ".").problems()[WheelField.CELEBRATE])
        assertFalse(form.copy(spinMax = "").isValid)
    }

    @Test
    fun `typing accepts digits and one point`() {
        assertTrue(WheelSettingsForm.isNumberDraft("12.5"))
        assertTrue(WheelSettingsForm.isNumberDraft(""))
        assertFalse(WheelSettingsForm.isNumberDraft("1.2.3"))
        assertFalse(WheelSettingsForm.isNumberDraft("-1"))
        assertEquals(2000, WheelSettingsForm.parseSecondsToMs(" 2 "))
        assertEquals(1001, WheelSettingsForm.parseSecondsToMs("1.0006"))
        assertEquals("80%", WheelSettingsForm.volumeLabel(0.8))
    }
}
