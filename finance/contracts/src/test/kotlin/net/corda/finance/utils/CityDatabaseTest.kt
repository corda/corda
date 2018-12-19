package net.corda.finance.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class CityDatabaseTest {
    @Test
    fun lookups() {
        val london = CityDatabase["London"]!!
        assertEquals(WorldCoordinate(51.5, -0.12), london.coordinate)
        assertEquals("GB", london.countryCode)
        assertEquals("London", london.description)
    }
}