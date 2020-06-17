package net.corda.node.services

import net.corda.client.rpc.CordaRPCClient
import net.corda.contracts.serialization.generics.DataObject
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.flows.serialization.generics.GenericTypeFlow
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

@Suppress("FunctionName")
class DeterministicContractWithGenericTypeTest {
    companion object {
        const val DATA_VALUE = 5000L

        @JvmField
        val logger = loggerFor<DeterministicContractWithGenericTypeTest>()

        @ClassRule
        @JvmField
        val djvmSources = DeterministicSourcesRule()
    }

    @Test(timeout=300_000)
	fun `test DJVM can deserialise command with generic type`() {
        val user = User("u", "p", setOf(Permissions.all()))
        driver(DriverParameters(
            portAllocation = incrementalPortAllocation(),
            startNodesInProcess = false,
            notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, validating = true)),
            cordappsForAllNodes = listOf(
                cordappWithPackages("net.corda.flows.serialization.generics").signed(),
                cordappWithPackages("net.corda.contracts.serialization.generics").signed()
            ),
            djvmBootstrapSource = djvmSources.bootstrap,
            djvmCordaSource = djvmSources.corda
        )) {
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
}