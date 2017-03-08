package com.r3.corda.doorman

import com.typesafe.config.ConfigException
import org.junit.Test
import java.io.File
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DoormanParametersTest {
    private val testDummyPath = ".${File.separator}testDummyPath.jks"

    @Test
    fun `parse mode flag arg correctly`() {
        assertEquals(DoormanParameters.Mode.CA_KEYGEN, DoormanParameters("--keygen").mode)
        assertEquals(DoormanParameters.Mode.ROOT_KEYGEN, DoormanParameters("--rootKeygen").mode)
        assertEquals(DoormanParameters.Mode.DOORMAN, DoormanParameters().mode)
    }

    @Test
    fun `command line arg should override config file`() {
        val params = DoormanParameters("--keystorePath", testDummyPath, "--port", "1000", "--configFile", javaClass.getResource("/node.conf").path)
        assertEquals(testDummyPath, params.keystorePath.toString())
        assertEquals(1000, params.port)

        val params2 = DoormanParameters("--configFile", javaClass.getResource("/node.conf").path)
        assertEquals(Paths.get("/opt/doorman/certificates/caKeystore.jks"), params2.keystorePath)
        assertEquals(8080, params2.port)
    }

    @Test
    fun `should fail when config missing`() {
        // dataSourceProperties is missing from node_fail.conf and it should fail when accessed, and shouldn't use default from reference.conf.
        val params = DoormanParameters("--keygen", "--keystorePath", testDummyPath, "--configFile", javaClass.getResource("/node_fail.conf").path)
        assertFailsWith<ConfigException.Missing> { params.dataSourceProperties }
    }
}
