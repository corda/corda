package net.corda.explorer.views

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.DecimalFormatSymbols
import java.util.*

class GuiUtilitiesKtTest {
    @Test
    fun `test to string with suffix`() {
        //Required for this test to be independent of the default Locale.
        val ds = DecimalFormatSymbols(Locale.getDefault()).decimalSeparator

        assertEquals("10${ds}5k", 10500.toStringWithSuffix())
        assertEquals("100", 100.toStringWithSuffix())
        assertEquals("5${ds}0M", 5000000.toStringWithSuffix())
        assertEquals("1${ds}0B", 1000000000.toStringWithSuffix())
        assertEquals("1${ds}5T", 1500000000000.toStringWithSuffix())
        assertEquals("1000${ds}0T", 1000000000000000.toStringWithSuffix())
    }
}