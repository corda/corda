package net.corda.node

import net.corda.core.internal.PLATFORM_VERSION
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.FINANCE_CONTRACTS_CORDAPP
import net.corda.testing.node.internal.FINANCE_WORKFLOWS_CORDAPP
import org.apache.commons.lang3.SystemUtils
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NodeRPCTests {
    private val CORDA_VERSION_REGEX = "\\d+(\\.\\d+)?(\\.\\d+)?(-\\w+)?".toRegex()
    private val CORDA_VENDOR = "Corda Open Source"
    private val CORDA_VENDOR_CE = "Corda Community Edition"
    private val CORDAPPS = listOf(FINANCE_CONTRACTS_CORDAPP, FINANCE_WORKFLOWS_CORDAPP)
    private val CORDAPP_TYPES = setOf("Contract CorDapp", "Workflow CorDapp")
    private val CLASSIFIER = if (SystemUtils.IS_JAVA_11) "-jdk11" else ""
    private val CORDAPP_CONTRACTS_NAME_REGEX = "corda-finance-contracts-$CORDA_VERSION_REGEX".toRegex()
    private val CORDAPP_WORKFLOWS_NAME_REGEX = "corda-finance-workflows-$CORDA_VERSION_REGEX$CLASSIFIER".toRegex()
    private val CORDAPP_SHORT_NAME = "Corda Finance Demo"
    private val CORDAPP_VENDOR = "R3"
    private val CORDAPP_LICENCE = "Open Source (Apache 2)"
    private val HEXADECIMAL_REGEX = "[0-9a-fA-F]+".toRegex()

    @Test(timeout=300_000)
	fun `run nodeDiagnosticInfo`() {
        driver(DriverParameters(notarySpecs = emptyList(), cordappsForAllNodes = CORDAPPS, extraCordappPackagesToScan = emptyList())) {
            val nodeDiagnosticInfo = startNode().get().rpc.nodeDiagnosticInfo()
            assertTrue(nodeDiagnosticInfo.version.matches(CORDA_VERSION_REGEX))
            assertEquals(PLATFORM_VERSION, nodeDiagnosticInfo.platformVersion)
            assertTrue(nodeDiagnosticInfo.vendor == CORDA_VENDOR || nodeDiagnosticInfo.vendor == CORDA_VENDOR_CE)
            nodeDiagnosticInfo.cordapps.forEach { println("${it.shortName} ${it.type}") }
            assertEquals(CORDAPPS.size, nodeDiagnosticInfo.cordapps.size)
            assertEquals(CORDAPP_TYPES, nodeDiagnosticInfo.cordapps.map { it.type }.toSet())
            assertTrue(nodeDiagnosticInfo.cordapps.any { it.name.matches(CORDAPP_CONTRACTS_NAME_REGEX) })
            assertTrue(nodeDiagnosticInfo.cordapps.any { it.name.matches(CORDAPP_WORKFLOWS_NAME_REGEX) })
            val cordappInfo = nodeDiagnosticInfo.cordapps.first()
            assertEquals(CORDAPP_SHORT_NAME, cordappInfo.shortName)
            assertTrue(cordappInfo.version.all { it.isDigit() })
            assertEquals(CORDAPP_VENDOR, cordappInfo.vendor)
            assertEquals(CORDAPP_LICENCE, cordappInfo.licence)
            assertTrue(cordappInfo.minimumPlatformVersion <= PLATFORM_VERSION)
            assertTrue(cordappInfo.targetPlatformVersion <= PLATFORM_VERSION)
            assertTrue(cordappInfo.jarHash.toString().matches(HEXADECIMAL_REGEX))
        }
    }
}