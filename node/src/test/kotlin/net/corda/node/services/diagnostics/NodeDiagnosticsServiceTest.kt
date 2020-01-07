package net.corda.node.services.diagnostics

import net.corda.common.logging.CordaVersion
import net.corda.core.contracts.ContractClassName
import net.corda.core.cordapp.Cordapp
import net.corda.core.cordapp.CordappContext
import net.corda.core.cordapp.CordappInfo
import net.corda.core.flows.FlowLogic
import net.corda.core.internal.cordapp.CordappImpl
import net.corda.core.node.services.AttachmentId
import net.corda.node.internal.cordapp.CordappProviderInternal
import org.junit.Test
import kotlin.test.assertEquals

class NodeDiagnosticsServiceTest {

    private val diagnosticsService = NodeDiagnosticsService()

    @Test
    fun `platform version info correctly returned from diagnostics service`() {
        val info = diagnosticsService.nodeVersionInfo()
        assertEquals(CordaVersion.releaseVersion, info.releaseVersion)
        assertEquals(CordaVersion.revision, info.revision)
        assertEquals(CordaVersion.platformVersion, info.platformVersion)
        assertEquals(CordaVersion.vendor, info.vendor)
    }
}