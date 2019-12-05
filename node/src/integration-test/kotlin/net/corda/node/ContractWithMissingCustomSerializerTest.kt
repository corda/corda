package net.corda.node

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.CordaRuntimeException
//import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.flows.serialization.missing.MissingSerializerFlow
import net.corda.node.services.Permissions
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.User
import net.corda.testing.node.internal.cordappWithPackages
import org.junit.Test
import org.junit.jupiter.api.assertThrows

@Suppress("FunctionName")
class ContractWithMissingCustomSerializerTest {
    companion object {
        const val BOBBINS = 5000L
    }

    @Test
    fun `flow with missing custom serializer by rpc`() {
        val user = User("u", "p", setOf(Permissions.all()))
        driver(DriverParameters(
            portAllocation = incrementalPortAllocation(),
            startNodesInProcess = false,
            notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, validating = true)),
            cordappsForAllNodes = listOf(
                cordappWithPackages("net.corda.flows.serialization.missing").signed(),
                cordappWithPackages("net.corda.contracts.serialization.missing.contract").signed()
            )
        )) {
            val alice = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            assertThrows<CordaRuntimeException> {
                CordaRPCClient(hostAndPort = alice.rpcAddress)
                    .start(user.username, user.password)
                    .proxy
                    .startFlow(::MissingSerializerFlow, BOBBINS)
                    .returnValue
                    .getOrThrow()
            }
        }
    }
}