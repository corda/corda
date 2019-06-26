package net.corda.node.services.keys.cryptoservice.bouncycastle

import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.node.utilities.registration.TestDoorman
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.internal.DEV_ROOT_CA
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import net.corda.testing.node.internal.SharedCompatibilityZoneParams
import net.corda.testing.node.internal.internalDriver
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.net.URL

class BouncyCastleWrappedKeysTest: IntegrationTest() {
    private val portAllocation = incrementalPortAllocation(13900)

    @Rule
    @JvmField
    val doorman = TestDoorman(portAllocation)

    companion object {
        private val notaryName = CordaX500Name("NotaryService", "Zurich", "CH")
        private val bankAName = CordaX500Name("BankA", "London", "GB")
        private val bankBName = CordaX500Name("BankB", "London", "GB")

        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(notaryName, bankAName, bankBName)
    }

    @Test
    fun `node performs transactions using confidential identities with wrapped keys successfully`() {
        val compatibilityZone = SharedCompatibilityZoneParams(
                URL("http://${doorman.serverHostAndPort}"),
                null,
                publishNotaries = { doorman.server.networkParameters = testNetworkParameters(it) },
                rootCert = DEV_ROOT_CA.certificate)

        internalDriver(
                portAllocation = portAllocation,
                compatibilityZone = compatibilityZone,
                notarySpecs = listOf(NotarySpec(notaryName)),
                cordappsForAllNodes = FINANCE_CORDAPPS,
                notaryCustomOverrides = mapOf("devMode" to false, "cordappSignerKeyFingerprintBlacklist" to listOf<String>())
        ) {
            val (bankA, bankB) = listOf(
                    startNode(providedName = bankAName, customOverrides = mapOf(
                            "devMode" to false,
                            "cordappSignerKeyFingerprintBlacklist" to listOf<String>(),
                            "freshIdentitiesConfiguration" to mapOf(
                                    "mode" to "DEGRADED_WRAPPED",
                                    "cryptoServiceConfiguration" to mapOf(
                                            "cryptoServiceName" to "BC_SIMPLE"
                                    ),
                                    "createDuringStartup" to "ONLY_IF_MISSING"
                            )
                    )),
                    startNode(providedName = bankBName, customOverrides = mapOf(
                            "devMode" to false,
                            "cordappSignerKeyFingerprintBlacklist" to listOf<String>()
                    ))
            ).transpose().getOrThrow()

            bankB.rpc.startFlow(
                    ::CashIssueFlow,
                    1000.DOLLARS,
                    OpaqueBytes.of(12),
                    defaultNotaryIdentity
            ).returnValue.getOrThrow()

            val result = bankB.rpc.startFlow(
                    ::CashPaymentFlow,
                    10.DOLLARS,
                    bankA.nodeInfo.singleIdentity(),
                    true,
                    defaultNotaryIdentity
            ).returnValue.getOrThrow()

            val bankAPubKey = bankA.rpc.nodeInfo().legalIdentities.first().owningKey
            // verify a confidential identity was used
            assertThat(result.recipient!!.owningKey.encoded!!).isNotEqualTo(bankAPubKey.encoded!!)
        }
    }
}