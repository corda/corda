package net.corda.node

import net.corda.client.rpc.CordaRPCClient
import net.corda.contracts.multiple.vulnerable.MutableDataObject
import net.corda.core.contracts.TransactionVerificationException.ContractRejection
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.flows.multiple.evil.EvilFlow
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
import kotlin.test.assertFailsWith

class EvilContractCannotModifyStatesTest {
    companion object {
        private val user = User("u", "p", setOf(Permissions.all()))
        private val evilFlowCorDapp = cordappWithPackages("net.corda.flows.multiple.evil").signed()
        private val evilContractCorDapp = cordappWithPackages("net.corda.contracts.multiple.evil").signed()
        private val vulnerableContractCorDapp = cordappWithPackages("net.corda.contracts.multiple.vulnerable").signed()

        private val NOTHING = MutableDataObject(0)

        fun driverParameters(runInProcess: Boolean): DriverParameters {
            return DriverParameters(
                portAllocation = incrementalPortAllocation(),
                startNodesInProcess = runInProcess,
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, startInProcess = runInProcess, validating = true)),
                cordappsForAllNodes = listOf(
                    vulnerableContractCorDapp,
                    evilContractCorDapp,
                    evilFlowCorDapp
                )
            )
        }
    }

    @Test(timeout = 300_000)
    fun testContractThatTriesToModifyStates() {
        val evilData = MutableDataObject(5000)
        driver(driverParameters(runInProcess = false)) {
            val alice = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val ex = assertFailsWith<ContractRejection> {
                CordaRPCClient(hostAndPort = alice.rpcAddress)
                    .start(user.username, user.password)
                    .use { client ->
                        client.proxy.startFlow(::EvilFlow, evilData).returnValue.getOrThrow()
                    }
            }
            assertThat(ex).hasMessageContaining("Purchase payment of $NOTHING should be at least ")
        }
    }
}
