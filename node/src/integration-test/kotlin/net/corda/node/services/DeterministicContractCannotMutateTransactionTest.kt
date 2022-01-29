package net.corda.node.services

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.flows.mutator.MutatorFlow
import net.corda.node.DeterministicSourcesRule
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.User
import net.corda.testing.node.internal.cordappWithPackages
import org.junit.ClassRule
import org.junit.Test

class DeterministicContractCannotMutateTransactionTest {
    companion object {
        private val logger = loggerFor<DeterministicContractCannotMutateTransactionTest>()
        private val user = User("u", "p", setOf(Permissions.all()))
        private val mutatorFlowCorDapp = cordappWithPackages("net.corda.flows.mutator").signed()
        private val mutatorContractCorDapp = cordappWithPackages("net.corda.contracts.mutator").signed()

        @ClassRule
        @JvmField
        val djvmSources = DeterministicSourcesRule()

        fun driverParameters(runInProcess: Boolean = false): DriverParameters {
            return DriverParameters(
                portAllocation = incrementalPortAllocation(),
                startNodesInProcess = runInProcess,
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, startInProcess = runInProcess, validating = true)),
                cordappsForAllNodes = listOf(mutatorContractCorDapp, mutatorFlowCorDapp),
                djvmBootstrapSource = djvmSources.bootstrap,
                djvmCordaSource = djvmSources.corda
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
