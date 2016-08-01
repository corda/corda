package com.r3corda.node.driver

import com.r3corda.node.services.messaging.ArtemisMessagingComponent
import com.r3corda.node.services.transactions.NotaryService
import org.junit.Test
import java.net.Socket
import java.net.SocketException


class DriverTests {

    @Test
    fun simpleNodeStartupShutdownWorks() {

        // Start a notary
        val (handle, notaryNodeInfo) = driver(quasarPath = "../lib/quasar.jar") {
            startNode(setOf(NotaryService.Type), "TestNotary")
        }
        // Check that the node is registered in the network map
        poll {
            handle.networkMapCache.get(NotaryService.Type).firstOrNull {
                it.identity.name == "TestNotary"
            }
        }
        // Check that the port is bound
        val address = notaryNodeInfo.address as ArtemisMessagingComponent.Address
        poll {
            try {
                Socket(address.hostAndPort.hostText, address.hostAndPort.port).close()
                Unit
            } catch (_exception: SocketException) {
                null
            }
        }

        // Shutdown
        handle.shutdown()
        // Check that the port is not bound
        poll {
            try {
                Socket(address.hostAndPort.hostText, address.hostAndPort.port).close()
                null
            } catch (_exception: SocketException) {
                Unit
            }
        }
    }
}
