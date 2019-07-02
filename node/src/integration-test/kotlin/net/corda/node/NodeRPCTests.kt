package net.corda.node

import kotlin.test.assertEquals
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.FINANCE_CONTRACTS_CORDAPP
import net.corda.testing.node.internal.FINANCE_WORKFLOWS_CORDAPP
import org.junit.Test
import kotlin.test.assertTrue

class NodeRPCTests {
    private val CORDA_VERSION_REGEX = "\\d+(\\.\\d+)?(-\\w+)?".toRegex() // e.g. "5.0-SNAPSHOT"
    private val CORDA_REVISION_REGEX = "[0-9a-fA-F]+".toRegex()
    private val CORDA_VENDOR = "Corda Open Source"
    private val CORDAPPS = listOf(FINANCE_CONTRACTS_CORDAPP, FINANCE_WORKFLOWS_CORDAPP)
    private val CORDAPP_NAME_REGEX = "corda-finance-contracts-$CORDA_VERSION_REGEX".toRegex()
    private val CORDAPP_SHORT_NAME = "Corda Finance Demo"
    private val CORDAPP_VERSION_REGEX = "\\d+".toRegex()
    private val CORDAPP_VENDOR = "R3"
    private val CORDAPP_LICENCE = "Open Source (Apache 2)"

    @Test
    fun `run nodeDiagnosticInfo`() {
        driver(DriverParameters(cordappsForAllNodes = CORDAPPS)) {
            val nodeDiagnosticInfo = startNode().get().rpc.nodeDiagnosticInfo()
            assertTrue(nodeDiagnosticInfo.cordaVersionInfo.version.matches(CORDA_VERSION_REGEX))
            assertTrue(nodeDiagnosticInfo.cordaVersionInfo.revision.matches(CORDA_REVISION_REGEX))
            assertEquals(PLATFORM_VERSION, nodeDiagnosticInfo.cordaVersionInfo.platformVersion)
            assertEquals(CORDA_VENDOR, nodeDiagnosticInfo.cordaVersionInfo.vendor)
            assertEquals(CORDAPPS.size, nodeDiagnosticInfo.cordapps.size)
            val cordappDiagnosticInfo = nodeDiagnosticInfo.cordapps.first()
            assertTrue(cordappDiagnosticInfo.name.matches(CORDAPP_NAME_REGEX))
            assertEquals(CORDAPP_SHORT_NAME, cordappDiagnosticInfo.shortName)
            assertTrue(cordappDiagnosticInfo.version.matches(CORDAPP_VERSION_REGEX))
            assertEquals(CORDAPP_VENDOR, cordappDiagnosticInfo.vendor)
            assertEquals(CORDAPP_LICENCE, cordappDiagnosticInfo.licence)
            assertTrue(cordappDiagnosticInfo.minimumPlatformVersion <= cordappDiagnosticInfo.targetPlatformVersion)
            assertTrue(cordappDiagnosticInfo.targetPlatformVersion <= PLATFORM_VERSION)
        }
    }
}