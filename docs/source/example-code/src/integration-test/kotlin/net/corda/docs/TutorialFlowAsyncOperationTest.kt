package net.corda.docs

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.docs.tutorial.flowstatemachines.ExampleSummingFlow
import net.corda.node.services.Permissions
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.junit.Test
import kotlin.test.assertEquals

class TutorialFlowAsyncOperationTest {
    // DOCSTART summingWorks
    @Test
    fun summingWorks() {
        driver(DriverParameters(startNodesInProcess = true)) {
            val aliceUser = User("aliceUser", "testPassword1", permissions = setOf(Permissions.all()))
            val alice = startNode(providedName = ALICE_NAME, rpcUsers = listOf(aliceUser)).getOrThrow()
            val aliceClient = CordaRPCClient(alice.rpcAddress)
            val aliceProxy = aliceClient.start("aliceUser", "testPassword1").proxy
            val answer = aliceProxy.startFlow(::ExampleSummingFlow).returnValue.getOrThrow()
            assertEquals(3, answer)
        }
    }
    // DOCEND summingWorks
}
