package com.r3.corda.doorman

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DoormanParametersTest {
    @Test
    fun `parse arg correctly`() {
        val params = DoormanParameters(arrayOf("--keygen", "--keystorePath", "./testDummyPath.jks"))
        assertEquals(DoormanParameters.Mode.CA_KEYGEN, params.mode)
        assertEquals("./testDummyPath.jks", params.keystorePath.toString())
        assertEquals(0, params.port)

        val params2 = DoormanParameters(arrayOf("--keystorePath", "./testDummyPath.jks", "--port", "1000"))
        assertEquals(DoormanParameters.Mode.DOORMAN, params2.mode)
        assertEquals("./testDummyPath.jks", params2.keystorePath.toString())
        assertEquals(1000, params2.port)
    }
}
