package net.corda.node

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.flows.mutator.MutatorFlow
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

class ContractCannotMutateTransactionTest {
    companion object {
        private val logger = loggerFor<ContractCannotMutateTransactionTest>()
        private val user = User("u", "p", setOf(Permissions.all()))
        private val mutatorFlowCorDapp = cordappWithPackages("net.corda.flows.mutator").signed()
        private val mutatorContractCorDapp = cordappWithPackages("net.corda.contracts.mutator").signed()

        fun driverParameters(runInProcess: Boolean = false): DriverParameters {
            return DriverParameters(
                portAllocation = incrementalPortAllocation(),
                startNodesInProcess = runInProcess,
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, startInProcess = runInProcess, validating = true)),
                cordappsForAllNodes = listOf(mutatorContractCorDapp, mutatorFlowCorDapp)
            )
        }
    }

    @Test(timeout = 300_000)
    fun testContractCannotModifyTransaction() {
        driver(driverParameters()) {
            val alice = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val txID = CordaRPCClient(hostAndPort = alice.rpcAddress)
                .start(user.username, user.password)
                .use { client ->
                    client.proxy.startFlow(::MutatorFlow).returnValue.getOrThrow()
                }
            logger.info("TX-ID: {}", txID)
        }
    }
}
