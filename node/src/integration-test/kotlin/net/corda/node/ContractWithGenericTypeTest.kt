package net.corda.node

import net.corda.client.rpc.CordaRPCClient
import net.corda.contracts.serialization.generics.DataObject
import net.corda.core.contracts.TransactionVerificationException.ContractRejection
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.flows.serialization.generics.GenericTypeFlow
import net.corda.node.services.Permissions
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.User
import net.corda.testing.node.internal.cordappWithPackages
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.jupiter.api.assertThrows

@Suppress("FunctionName")
class ContractWithGenericTypeTest {
    companion object {
        const val DATA_VALUE = 5000L

        @JvmField
        val logger = loggerFor<ContractWithGenericTypeTest>()

        @JvmField
        val user = User("u", "p", setOf(Permissions.all()))

        fun parameters(): DriverParameters {
            return DriverParameters(
                portAllocation = incrementalPortAllocation(),
                startNodesInProcess = false,
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, validating = true)),
                cordappsForAllNodes = listOf(
                    cordappWithPackages("net.corda.flows.serialization.generics").signed(),
                    cordappWithPackages("net.corda.contracts.serialization.generics").signed()
                )
            )
        }
    }

    @Test(timeout = 300_000)
	fun `flow with value of generic type`() {
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
    fun `flow without value of generic type`() {
        driver(parameters()) {
            val alice = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val ex = assertThrows<ContractRejection> {
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