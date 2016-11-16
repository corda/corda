package net.corda.explorer.views

import org.junit.Assert.assertEquals
import org.junit.Test

class GuiUtilitiesKtTest {
    @Test
    fun `test to string with suffix`() {
        assertEquals("10.5k", 10500.toStringWithSuffix())
        assertEquals("100", 100.toStringWithSuffix())
        assertEquals("5.0M", 5000000.toStringWithSuffix())
        assertEquals("1.0B", 1000000000.toStringWithSuffix())
        assertEquals("1.5T", 1500000000000.toStringWithSuffix())
        assertEquals("1000.0T", 1000000000000000.toStringWithSuffix())
    }
}