package net.corda.node.services

import net.corda.contracts.serialization.custom.Currantsy
import net.corda.contracts.serialization.custom.CustomSerializerContract
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.flows.serialization.custom.CustomSerializerFlow
import net.corda.node.DeterministicSourcesRule
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
class DeterministicContractWithCustomSerializerTest {
    companion object {
        val logger = loggerFor<DeterministicContractWithCustomSerializerTest>()
        const val GOOD_CURRANTS = 1201L
        const val BAD_CURRANTS = 4703L

        @ClassRule
        @JvmField
        val djvmSources = DeterministicSourcesRule()

        @JvmField
        val flowCordapp = cordappWithPackages("net.corda.flows.serialization.custom").signed()

        @JvmField
        val contractCordapp = cordappWithPackages("net.corda.contracts.serialization.custom").signed()

        fun parametersFor(djvmSources: DeterministicSourcesRule, vararg cordapps: TestCordapp): DriverParameters {
            return DriverParameters(
                portAllocation = incrementalPortAllocation(),
                startNodesInProcess = false,
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, validating = true)),
                cordappsForAllNodes = cordapps.toList(),
                djvmBootstrapSource = djvmSources.bootstrap,
                djvmCordaSource = djvmSources.corda
            )
        }

        @BeforeClass
        @JvmStatic
        fun checkData() {
            assertNotCordaSerializable<Currantsy>()
        }
    }

    @Test
    fun `test DJVM can verify using custom serializer`() {
        driver(parametersFor(djvmSources, flowCordapp, contractCordapp)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val txId = assertDoesNotThrow {
                alice.rpc.startFlow(::CustomSerializerFlow, Currantsy(GOOD_CURRANTS))
                    .returnValue.getOrThrow()
            }
            logger.info("TX-ID: {}", txId)
        }
    }

    @Test
    fun `test DJVM can fail verify using custom serializer`() {
        driver(parametersFor(djvmSources, flowCordapp, contractCordapp)) {
            val alice = startNode(providedName = ALICE_NAME).getOrThrow()
            val currantsy = Currantsy(BAD_CURRANTS)
            val ex = assertThrows<DeterministicVerificationException> {
                alice.rpc.startFlow(::CustomSerializerFlow, currantsy)
                    .returnValue.getOrThrow()
            }
            assertThat(ex)
                .hasMessageStartingWith("sandbox.net.corda.core.contracts.TransactionVerificationException\$ContractRejection -> ")
                .hasMessageContaining(" Contract verification failed: Too many currants! $currantsy is unraisinable!, ")
                .hasMessageContaining(" contract: sandbox.${CustomSerializerContract::class.java.name}, ")
        }
    }
}
