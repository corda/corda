package net.corda.node.services

import net.corda.contracts.djvm.whitelist.DeterministicWhitelistContract
import net.corda.contracts.djvm.whitelist.WhitelistData
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.flows.djvm.whitelist.DeterministicWhitelistFlow
import net.corda.node.DeterministicSourcesRule
import net.corda.node.OutOfProcessSecurityRule
import net.corda.node.assertNotCordaSerializable
import net.corda.node.internal.djvm.DeterministicVerificationException
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.TestCordapp
import net.corda.testing.node.internal.cordappWithPackages
import org.assertj.core.api.Assertions.assertThat
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

@Suppress("FunctionName")
class DeterministicContractWithSerializationWhitelistTest {
    companion object {
        val logger = loggerFor<DeterministicContractWithSerializationWhitelistTest>()
        const val GOOD_VALUE = 1201L
        const val BAD_VALUE = 6333L

        @ClassRule
        @JvmField
        val djvmSources = DeterministicSourcesRule()

        @ClassRule
        @JvmField
        val security = OutOfProcessSecurityRule()

        @JvmField
        val flowCordapp = cordappWithPackages("net.corda.flows.djvm.whitelist").signed()

        @JvmField
        val contractCordapp = cordappWithPackages("net.corda.contracts.djvm.whitelist").signed()

        fun parametersFor(djvmSources: DeterministicSourcesRule, vararg cordapps: TestCordapp): DriverParameters {
            return DriverParameters(
                portAllocation = incrementalPortAllocation(),
                startNodesInProcess = false,
                systemProperties = security.systemProperties,
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, validating = true)),
                cordappsForAllNodes = cordapps.toList(),
                djvmBootstrapSource = djvmSources.bootstrap,
                djvmCordaSource = djvmSources.corda
            )
        }

        @BeforeClass
        @JvmStatic
        fun checkData() {
            assertNotCordaSerializable<WhitelistData>()
        }
    }

    @Test(timeout=300_000)
	fun `test DJVM can verify using whitelist`() {
        driver(parametersFor(djvmSources, flowCordapp, contractCordapp)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val txId = assertDoesNotThrow {
                alice.rpc.startFlow(::DeterministicWhitelistFlow, WhitelistData(GOOD_VALUE))
                    .returnValue.getOrThrow()
            }
            logger.info("TX-ID: {}", txId)
        }
    }

    @Test(timeout=300_000)
	fun `test DJVM can fail verify using whitelist`() {
        driver(parametersFor(djvmSources, flowCordapp, contractCordapp)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val badData = WhitelistData(BAD_VALUE)
            val ex = assertThrows<DeterministicVerificationException> {
                alice.rpc.startFlow(::DeterministicWhitelistFlow, badData)
                    .returnValue.getOrThrow()
            }
            assertThat(ex)
                .hasMessageStartingWith("sandbox.net.corda.core.contracts.TransactionVerificationException\$ContractRejection -> ")
                .hasMessageContaining(" Contract verification failed: WhitelistData $badData exceeds maximum value!, ")
                .hasMessageContaining(" contract: sandbox.${DeterministicWhitelistContract::class.java.name}, ")
        }
    }
}
