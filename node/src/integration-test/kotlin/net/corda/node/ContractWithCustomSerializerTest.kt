package net.corda.node

import net.corda.client.rpc.CordaRPCClient
import net.corda.contracts.serialization.custom.Currancy
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.flows.serialization.custom.CustomSerializerFlow
import net.corda.node.services.Permissions
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.User
import net.corda.testing.node.internal.CustomCordapp
import net.corda.testing.node.internal.cordappWithPackages
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import kotlin.test.assertFailsWith

@Suppress("FunctionName")
class ContractWithCustomSerializerTest {
    companion object {
        const val CURRANTS = 5000L
        const val DEFAULT_STARTING_PORT = 10000
    }

    @Test
    fun `flow with custom serializer by rpc`() {
        val user = User("u", "p", setOf(Permissions.all()))
        driver(DriverParameters(
            portAllocation = incrementalPortAllocation(DEFAULT_STARTING_PORT),
            startNodesInProcess = false,
            notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, validating = true)),
            cordappsForAllNodes = listOf(
                cordappWithPackages("net.corda.flows.serialization.custom"),
                CustomCordapp(
                    packages = setOf("net.corda.contracts.serialization.custom"),
                    name = "has-custom-serializer"
                ).signed()
            )
        )) {
            val alice = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val ex = assertFailsWith<TransactionVerificationException> {
                CordaRPCClient(hostAndPort = alice.rpcAddress)
                    .start(user.username, user.password)
                    .proxy
                    .startFlow(::CustomSerializerFlow, Currancy(CURRANTS))
                    .returnValue
                    .getOrThrow()
            }
            assertThat(ex).hasMessageContaining("Too many currants! $CURRANTS is unraisinable!")
        }
    }
}