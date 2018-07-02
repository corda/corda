package net.corda.node

import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.errors.PortBindingException
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.driver
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket

class NodeStartupTests {

    @Test
    fun `node throws detailed exception if it cannot bind to a port`() {

        val portAllocation = PortAllocation.Incremental(20000)
        val busyPort = portAllocation.portCounter.get()
        ServerSocket().use { socket ->
            socket.bind(InetSocketAddress(busyPort))
            driver(DriverParameters(startNodesInProcess = true, portAllocation = portAllocation)) {
                assertThatThrownBy { startNode().getOrThrow() }.isInstanceOfSatisfying(PortBindingException::class.java) { exception ->
                    assertThat(exception.port).isEqualTo(busyPort).withFailMessage("Expected port to be $busyPort but was ${exception.port}.")
                }
            }
        }
    }
}