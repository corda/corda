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

    private val cordappProviderMock = object : CordappProviderInternal {
        override val cordapps: MutableList<CordappImpl> = mutableListOf()

        override fun getCordappForFlow(flowLogic: FlowLogic<*>): Cordapp? {
            throw NotImplementedError()
        }

        override fun getAppContext(): CordappContext {
            throw NotImplementedError()
        }

        override fun getContractAttachmentID(contractClassName: ContractClassName): AttachmentId? {
            throw NotImplementedError()
        }
    }

    private val diagnosticsService = NodeDiagnosticsService(cordappProviderMock)

    private fun createCordappInfo(cordappImpl: CordappImpl) : CordappInfo {
        val typeString = when (cordappImpl.info) {
            is Cordapp.Info.Contract -> "Contract CorDapp"
            is Cordapp.Info.Workflow -> "Workflow CorDapp"
            else -> "CorDapp"
        }
        return CordappInfo(
                typeString,
                cordappImpl.name,
                cordappImpl.info.shortName,
                cordappImpl.minimumPlatformVersion,
                cordappImpl.targetPlatformVersion,
                cordappImpl.info.version,
                cordappImpl.info.vendor,
                cordappImpl.info.licence,
                cordappImpl.jarHash
        )
    }

    @Test
    fun `platform version info correctly returned from diagnostics service`() {
        val info = diagnosticsService.nodeDiagnosticInfo()
        assertEquals(CordaVersion.releaseVersion, info.version)
        assertEquals(CordaVersion.revision, info.revision)
        assertEquals(CordaVersion.platformVersion, info.platformVersion)
        assertEquals(CordaVersion.vendor, info.vendor)
    }

    @Test
    fun `cordapp data correctly returned from diagnostics service`() {
        // First try with no CorDapps installed
        val noCordapps = diagnosticsService.nodeDiagnosticInfo()
        assertEquals(emptyList(), noCordapps.cordapps)

        cordappProviderMock.cordapps.add(CordappImpl.TEST_INSTANCE)
        val cordapp = diagnosticsService.nodeDiagnosticInfo()
        assertEquals(listOf(createCordappInfo(CordappImpl.TEST_INSTANCE)), cordapp.cordapps)
    }
}