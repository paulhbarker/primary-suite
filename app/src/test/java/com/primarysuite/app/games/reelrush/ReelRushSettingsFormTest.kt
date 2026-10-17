package com.primarysuite.app.games.reelrush

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelRushSettingsFormTest {

    private val defaults = ReelRushSettings.DEFAULTS
    private val form = SettingsForm.from(defaults)

    @Test
    fun `the default form shows seconds without trailing zeros`() {
        assertEquals("1", form.roundMin)
        assertEquals("10", form.roundMax)
        assertEquals("0.25", form.dwellMin)
        assertEquals("1.5", form.dwellMax)
        assertEquals("25", form.spinSpeed)
        assertEquals("50", form.displayLag)
        assertTrue(form.isValid)
    }

    @Test
    fun `a form converts back to the same settings`() {
        assertEquals(defaults, form.toSettings())
    }

    @Test
    fun `seconds go out as rounded milliseconds`() {
        val s = form.copy(roundMin = "1.5", roundMax = "12.345", dwellMin = "0.1", dwellMax = "2").toSettings()!!
        assertEquals(1500, s.roundMinMs)
        assertEquals(12345, s.roundMaxMs)
        assertEquals(100, s.dwellMinMs)
        assertEquals(2000, s.dwellMaxMs)
        // 0.1 * 1000 is 100.00000000000001 in floating point.
        assertEquals(100, SettingsForm.parseSecondsToMs("0.1"))
        assertEquals(1, SettingsForm.parseSecondsToMs("0.0005"))
    }

    @Test
    fun `fractional seconds in fields format cleanly`() {
        assertEquals("0.1", SettingsForm.formatSeconds(100))
        assertEquals("30", SettingsForm.formatSeconds(30000))
        assertEquals("12.345", SettingsForm.formatSeconds(12345))
    }

    @Test
    fun `blank and stray text are not numbers`() {
        val p = form.copy(reels = "", spinSpeed = ".", roundMin = "x").problems()
        assertEquals(FieldProblem.NOT_A_NUMBER, p[SettingsField.REELS])
        assertEquals(FieldProblem.NOT_A_NUMBER, p[SettingsField.SPIN_SPEED])
        assertEquals(FieldProblem.NOT_A_NUMBER, p[SettingsField.ROUND_MIN])
        assertNull(form.copy(reels = "").toSettings())
    }

    @Test
    fun `values outside the server's ranges are flagged`() {
        val p = form.copy(
            reels = "6", rounds = "0", nearMisses = "5", displayLag = "301",
            spinSpeed = "14.9", roundMin = "0.5", dwellMax = "5.1",
        ).problems()
        assertEquals(FieldProblem.OUT_OF_RANGE, p[SettingsField.REELS])
        assertEquals(FieldProblem.OUT_OF_RANGE, p[SettingsField.ROUNDS])
        assertEquals(FieldProblem.OUT_OF_RANGE, p[SettingsField.NEAR_MISSES])
        assertEquals(FieldProblem.OUT_OF_RANGE, p[SettingsField.DISPLAY_LAG])
        assertEquals(FieldProblem.OUT_OF_RANGE, p[SettingsField.SPIN_SPEED])
        assertEquals(FieldProblem.OUT_OF_RANGE, p[SettingsField.ROUND_MIN])
        assertEquals(FieldProblem.OUT_OF_RANGE, p[SettingsField.DWELL_MAX])
        assertFalse(form.copy(reels = "6").isValid)
    }

    @Test
    fun `the ends of every range are accepted`() {
        assertTrue(form.copy(reels = "2", rounds = "50", nearMisses = "0", displayLag = "300", spinSpeed = "40", roundMin = "1", roundMax = "30", dwellMin = "0.1", dwellMax = "5").isValid)
        assertTrue(form.copy(reels = "5", rounds = "1", nearMisses = "4", displayLag = "0", spinSpeed = "15").isValid)
    }

    @Test
    fun `longest must not be shorter than shortest`() {
        val p = form.copy(roundMin = "8", roundMax = "5", dwellMin = "2", dwellMax = "1").problems()
        assertEquals(FieldProblem.MAX_BELOW_MIN, p[SettingsField.ROUND_MAX])
        assertEquals(FieldProblem.MAX_BELOW_MIN, p[SettingsField.DWELL_MAX])
        assertTrue(form.copy(roundMin = "5", roundMax = "5").isValid)
    }

    @Test
    fun `number drafts allow one dot and digits only`() {
        assertTrue(SettingsForm.isNumberDraft(""))
        assertTrue(SettingsForm.isNumberDraft("1."))
        assertTrue(SettingsForm.isNumberDraft(".5"))
        assertFalse(SettingsForm.isNumberDraft("1.2.3"))
        assertFalse(SettingsForm.isNumberDraft("-1"))
        assertFalse(SettingsForm.isNumberDraft("1e3"))
    }

    @Test
    fun `volume label is a percentage`() {
        assertEquals("80%", SettingsForm.volumeLabel(0.8))
        assertEquals("5%", SettingsForm.volumeLabel(0.05))
    }

    @Test
    fun `names and toggles carry through`() {
        val s = form.copy(hardMode = true, assist = true, rowDecoys = false, names = listOf("a", "b", "", "d"), volume = 0.35).toSettings()!!
        assertTrue(s.hardMode)
        assertTrue(s.assist)
        assertFalse(s.rowDecoys)
        assertEquals(listOf("a", "b", "", "d"), s.names)
        assertEquals(0.35, s.volume, 0.0)
    }
}
