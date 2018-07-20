package net.corda.docs

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.docs.tutorial.flowstatemachines.ExampleSummingFlow
import net.corda.node.services.Permissions
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.toDatabaseSchemaName
import net.corda.testing.node.User
import org.junit.ClassRule
import org.junit.Test
import kotlin.test.assertEquals

class TutorialFlowAsyncOperationTest  : IntegrationTest() {
    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(ALICE_NAME.toDatabaseSchemaName(), DUMMY_NOTARY_NAME.toDatabaseSchemaName())
    }

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
