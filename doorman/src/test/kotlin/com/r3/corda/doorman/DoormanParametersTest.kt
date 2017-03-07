package com.r3.corda.doorman

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

class DoormanParametersTest {

    private val testDummyPath = ".${File.separator}testDummyPath.jks"

    @Test
    fun `parse arg correctly`() {
        val ref = javaClass.getResource("/reference.conf")
        val params = DoormanParameters(arrayOf("--keygen", "--keystorePath", testDummyPath, "--configFile", ref.path))
        assertEquals(DoormanParameters.Mode.CA_KEYGEN, params.mode)
        assertEquals(testDummyPath, params.keystorePath.toString())
        assertEquals(0, params.port)

        val params2 = DoormanParameters(arrayOf("--keystorePath", testDummyPath, "--port", "1000"))
        assertEquals(DoormanParameters.Mode.DOORMAN, params2.mode)
        assertEquals(testDummyPath, params2.keystorePath.toString())
        assertEquals(1000, params2.port)
    }
}
