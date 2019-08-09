package net.corda.node.services.keys.cryptoservice

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
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import net.corda.testing.node.internal.SharedCompatibilityZoneParams
import net.corda.testing.node.internal.internalDriver
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.net.URL
import java.nio.file.Path

internal val notaryName = CordaX500Name("NotaryService", "Zurich", "CH")
internal val aliceName = CordaX500Name("Alice", "London", "GB")
internal val genevieveName = CordaX500Name("Genevieve", "London", "GB")

abstract class AbstractWrappedKeysTest: IntegrationTest() {

    internal abstract fun configPath(): Path?

    internal abstract fun cryptoServiceName(): String

    internal abstract fun mode(): String

    internal abstract fun deleteEntries(aliases: List<String>)

    private val portAllocation = incrementalPortAllocation()

    @Rule
    @JvmField
    val doorman = TestDoorman(portAllocation)

    @After
    fun after() {
        deleteEntries(listOf("wrapping-key-alias"))
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
            val (alice, genevieve) = listOf(
                    startNode(providedName = aliceName, customOverrides = mapOf(
                            "devMode" to false,
                            "cordappSignerKeyFingerprintBlacklist" to listOf<String>(),
                            "freshIdentitiesConfiguration" to mapOf(
                                    "mode" to mode(),
                                    "cryptoServiceConfiguration" to mapOf(
                                            "cryptoServiceName" to cryptoServiceName(),
                                            "cryptoServiceConf" to configPath()?.toFile()?.absolutePath
                                    ),
                                    "createDuringStartup" to "ONLY_IF_MISSING"
                            )
                    )),
                    startNode(providedName = genevieveName, customOverrides = mapOf(
                            "devMode" to false,
                            "cordappSignerKeyFingerprintBlacklist" to listOf<String>()
                    ))
            ).transpose().getOrThrow()

            genevieve.rpc.startFlow(
                    ::CashIssueFlow,
                    1000.DOLLARS,
                    OpaqueBytes.of(12),
                    defaultNotaryIdentity
            ).returnValue.getOrThrow()

            val result = genevieve.rpc.startFlow(
                    ::CashPaymentFlow,
                    10.DOLLARS,
                    alice.nodeInfo.singleIdentity(),
                    true,
                    defaultNotaryIdentity
            ).returnValue.getOrThrow()

            val alicePubKey = alice.rpc.nodeInfo().legalIdentities.first().owningKey
            // verify a confidential identity was used
            assertThat(result.recipient!!.owningKey.encoded!!).isNotEqualTo(alicePubKey.encoded!!)
        }
    }

}