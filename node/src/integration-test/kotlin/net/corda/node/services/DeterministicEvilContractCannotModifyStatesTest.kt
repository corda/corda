package net.corda.node.services

import net.corda.client.rpc.CordaRPCClient
import net.corda.contracts.multiple.vulnerable.MutableDataObject
import net.corda.contracts.multiple.vulnerable.VulnerablePaymentContract
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.flows.multiple.evil.EvilFlow
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
import kotlin.test.assertFailsWith

class DeterministicEvilContractCannotModifyStatesTest {
    companion object {
        private val user = User("u", "p", setOf(Permissions.all()))
        private val evilFlowCorDapp = cordappWithPackages("net.corda.flows.multiple.evil").signed()
        private val evilContractCorDapp = cordappWithPackages("net.corda.contracts.multiple.evil").signed()
        private val vulnerableContractCorDapp = cordappWithPackages("net.corda.contracts.multiple.vulnerable").signed()

        private val NOTHING = MutableDataObject(0)

        @ClassRule
        @JvmField
        val djvmSources = DeterministicSourcesRule()

        fun driverParameters(runInProcess: Boolean = false): DriverParameters {
            return DriverParameters(
                portAllocation = incrementalPortAllocation(),
                startNodesInProcess = runInProcess,
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, startInProcess = runInProcess, validating = true)),
                cordappsForAllNodes = listOf(
                    vulnerableContractCorDapp,
                    evilContractCorDapp,
                    evilFlowCorDapp
                ),
                djvmBootstrapSource = djvmSources.bootstrap,
                djvmCordaSource = djvmSources.corda
            )
        }
    }

    @Test(timeout = 300_000)
    fun testContractThatTriesToModifyStates() {
        val evilData = MutableDataObject(5000)
        driver(driverParameters()) {
            val alice = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val ex = assertFailsWith<DeterministicVerificationException> {
                CordaRPCClient(hostAndPort = alice.rpcAddress)
                    .start(user.username, user.password)
                    .use { client ->
                        client.proxy.startFlow(::EvilFlow, evilData).returnValue.getOrThrow()
                    }
            }
            assertThat(ex)
                .hasMessageStartingWith("sandbox.net.corda.core.contracts.TransactionVerificationException\$ContractRejection -> ")
                .hasMessageContaining(" Contract verification failed: Failed requirement: Purchase payment of $NOTHING should be at least ")
                .hasMessageContaining(", contract: sandbox.${VulnerablePaymentContract::class.java.name}, ")
        }
    }
}
