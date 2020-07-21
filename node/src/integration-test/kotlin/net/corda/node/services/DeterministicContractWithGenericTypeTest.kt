package net.corda.node.services

import net.corda.client.rpc.CordaRPCClient
import net.corda.contracts.serialization.generics.DataObject
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.flows.serialization.generics.GenericTypeFlow
import net.corda.node.DeterministicSourcesRule
import net.corda.node.internal.djvm.DeterministicVerificationException
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.User
import net.corda.testing.node.internal.cordappWithPackages
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test
import org.junit.jupiter.api.assertThrows

@Suppress("FunctionName")
class DeterministicContractWithGenericTypeTest {
    companion object {
        const val DATA_VALUE = 5000L

        @JvmField
        val logger = loggerFor<DeterministicContractWithGenericTypeTest>()

        @JvmField
        val user = User("u", "p", setOf(Permissions.all()))

        @ClassRule
        @JvmField
        val djvmSources = DeterministicSourcesRule()

        fun parameters(): DriverParameters {
            return DriverParameters(
                portAllocation = incrementalPortAllocation(),
                startNodesInProcess = false,
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, validating = true)),
                cordappsForAllNodes = listOf(
                    cordappWithPackages("net.corda.flows.serialization.generics").signed(),
                    cordappWithPackages("net.corda.contracts.serialization.generics").signed()
                ),
                djvmBootstrapSource = djvmSources.bootstrap,
                djvmCordaSource = djvmSources.corda
            )
        }
    }

    @Test(timeout = 300_000)
	fun `test DJVM can deserialise command with generic type`() {
        driver(parameters()) {
            val alice = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val txID = CordaRPCClient(hostAndPort = alice.rpcAddress)
                .start(user.username, user.password)
                .use { client ->
                    client.proxy.startFlow(::GenericTypeFlow, DataObject(DATA_VALUE))
                        .returnValue
                        .getOrThrow()
                }
            logger.info("TX-ID=$txID")
        }
    }

    @Test(timeout = 300_000)
    fun `test DJVM can deserialise command without value of generic type`() {
        driver(parameters()) {
            val alice = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val ex = assertThrows<DeterministicVerificationException> {
                CordaRPCClient(hostAndPort = alice.rpcAddress)
                    .start(user.username, user.password)
                    .use { client ->
                        client.proxy.startFlow(::GenericTypeFlow, null)
                            .returnValue
                            .getOrThrow()
                    }
            }
            assertThat(ex).hasMessageContaining("Contract verification failed: Failed requirement: Purchase has a price,")
        }
    }
}