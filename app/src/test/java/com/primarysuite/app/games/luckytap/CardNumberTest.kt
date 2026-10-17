package com.primarysuite.app.games.luckytap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardNumberTest {

    @Test
    fun `accepts 1 to 100`() {
        assertEquals(1, CardNumber.parse("1"))
        assertEquals(42, CardNumber.parse("42"))
        assertEquals(100, CardNumber.parse("100"))
        assertEquals(7, CardNumber.parse(" 7 "))
    }

    @Test
    fun `rejects everything else`() {
        listOf("", "0", "101", "-5", "abc", "4.2", "1e2").forEach { assertNull(it, CardNumber.parse(it)) }
    }
}
