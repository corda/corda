package net.corda.node.services.rpc

import net.corda.core.utilities.getOrThrow
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.Test

class NodeHandleTests {
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