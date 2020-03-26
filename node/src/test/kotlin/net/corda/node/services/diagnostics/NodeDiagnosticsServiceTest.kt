package net.corda.node.services.diagnostics

import net.corda.common.logging.CordaVersion
import org.junit.Test
import kotlin.test.assertEquals

class NodeDiagnosticsServiceTest {

    private val diagnosticsService = NodeDiagnosticsService()

    @Test(timeout=300_000)
	fun `platform version info correctly returned from diagnostics service`() {
        val info = diagnosticsService.nodeVersionInfo()
        assertEquals(CordaVersion.releaseVersion, info.releaseVersion)
        assertEquals(CordaVersion.revision, info.revision)
        assertEquals(CordaVersion.platformVersion, info.platformVersion)
        assertEquals(CordaVersion.vendor, info.vendor)
    }
}