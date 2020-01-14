package net.corda.node

import net.corda.client.rpc.CordaRPCClient
import net.corda.contracts.fixup.dependent.DependentData
import net.corda.contracts.fixup.standalone.StandAloneData
import net.corda.core.CordaRuntimeException
import net.corda.core.contracts.TransactionVerificationException.ContractRejection
import net.corda.core.internal.hash
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.flows.fixup.CordappFixupFlow
import net.corda.node.services.Permissions
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.TestCordapp
import net.corda.testing.node.User
import net.corda.testing.node.internal.cordappWithFixups
import net.corda.testing.node.internal.cordappWithPackages
import org.assertj.core.api.Assertions.assertThat
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertFailsWith

@Suppress("FunctionName")
class ContractWithCordappFixupTest {
    companion object {
        const val BEANS = 10001L

        val user = User("u", "p", setOf(Permissions.all()))
        val flowCorDapp = cordappWithPackages("net.corda.flows.fixup").signed()
        val dependentContractCorDapp = cordappWithPackages("net.corda.contracts.fixup.dependent").signed()
        val standaloneContractCorDapp = cordappWithPackages("net.corda.contracts.fixup.standalone").signed()

        fun driverParameters(cordapps: List<TestCordapp>): DriverParameters {
            return DriverParameters(
                portAllocation = incrementalPortAllocation(),
                startNodesInProcess = false,
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, validating = true)),
                cordappsForAllNodes = cordapps,
                systemProperties = mapOf("net.corda.transactionbuilder.missingclass.disabled" to true.toString())
            )
        }

        @BeforeClass
        @JvmStatic
        fun checkData() {
            assertNotCordaSerializable<DependentData>()
            assertNotCordaSerializable<StandAloneData>()
        }
    }

    /*
     * Test that we can still build a transaction for a CorDapp with an implicit dependency.
     */
    @Test
    fun `flow with missing cordapp dependency with fixup`() {
        val dependentContractId = dependentContractCorDapp.jarFile.hash
        val standaloneContractId = standaloneContractCorDapp.jarFile.hash
        val fixupCorDapp = cordappWithFixups(listOf(
            setOf(dependentContractId) to setOf(dependentContractId, standaloneContractId)
        )).signed()
        val data = DependentData(BEANS)

        driver(driverParameters(listOf(flowCorDapp, dependentContractCorDapp, standaloneContractCorDapp, fixupCorDapp))) {
            val alice = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val ex = assertFailsWith<ContractRejection> {
                CordaRPCClient(hostAndPort = alice.rpcAddress)
                    .start(user.username, user.password)
                    .use { client ->
                        client.proxy.startFlow(::CordappFixupFlow, data).returnValue.getOrThrow()
                    }
            }
            assertThat(ex).hasMessageContaining("Invalid data: $data")
        }
    }

    /**
     * Test that our dependency is indeed missing and so requires fixing up.
     */
    @Test
    fun `flow with missing cordapp dependency without fixup`() {
        driver(driverParameters(listOf(flowCorDapp, dependentContractCorDapp, standaloneContractCorDapp))) {
            val alice = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val ex = assertFailsWith<CordaRuntimeException> {
                CordaRPCClient(hostAndPort = alice.rpcAddress)
                    .start(user.username, user.password)
                    .use { client ->
                        client.proxy.startFlow(::CordappFixupFlow, DependentData(BEANS)).returnValue.getOrThrow()
                    }
            }
            assertThat(ex).hasMessageContaining("Type ${StandAloneData::class.java.name} not present")
        }
    }
}