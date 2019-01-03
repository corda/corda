package net.corda.node.services.rpc

import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.ClassRule
import org.junit.Test

class NodeHandleTests : IntegrationTest() {
    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(ALICE_NAME, BOB_NAME, DUMMY_BANK_A_NAME, DUMMY_NOTARY_NAME)
    }

    @Test
    fun object_defined_functions_are_static_for_node_rpc_ops() {
        driver(DriverParameters(startNodesInProcess = true)) {
            val rpcClient = startNode().getOrThrow().rpc

            assertThatCode { rpcClient.hashCode() }.doesNotThrowAnyException()
            @Suppress("UnusedEquals")
            assertThatCode { rpcClient == rpcClient }.doesNotThrowAnyException()
            assertThatCode { rpcClient.toString() }.doesNotThrowAnyException()
        }
    }
}