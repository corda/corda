package com.r3corda.node.driver

import com.google.common.net.HostAndPort
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.node.services.NetworkMapCache
import com.r3corda.node.services.api.RegulatorService
import com.r3corda.node.services.messaging.ArtemisMessagingComponent
import com.r3corda.node.services.transactions.NotaryService
import org.junit.Test
import java.net.Socket
import java.net.SocketException


class DriverTests {

    companion object {
        fun addressMustBeBound(hostAndPort: HostAndPort) {
            poll {
                try {
                    Socket(hostAndPort.hostText, hostAndPort.port).close()
                    Unit
                } catch (_exception: SocketException) {
                    null
                }
            }
        }

        fun addressMustNotBeBound(hostAndPort: HostAndPort) {
            poll {
                try {
                    Socket(hostAndPort.hostText, hostAndPort.port).close()
                    null
                } catch (_exception: SocketException) {
                    Unit
                }
            }
        }

        fun nodeMustBeUp(networkMapCache: NetworkMapCache, nodeInfo: NodeInfo, nodeName: String) {
            val address = nodeInfo.address as ArtemisMessagingComponent.Address
            // Check that the node is registered in the network map
            poll {
                networkMapCache.get().firstOrNull {
                    it.identity.name == nodeName
                }
            }
            // Check that the port is bound
            addressMustBeBound(address.hostAndPort)
        }

        fun nodeMustBeDown(nodeInfo: NodeInfo) {
            val address = nodeInfo.address as ArtemisMessagingComponent.Address
            // Check that the port is bound
            addressMustNotBeBound(address.hostAndPort)
        }
    }

    @Test
    fun simpleNodeStartupShutdownWorks() {
        val (notary, regulator) = driver(quasarJarPath = "../lib/quasar.jar") {
            val notary = startNode("TestNotary", setOf(NotaryService.Type))
            val regulator = startNode("Regulator", setOf(RegulatorService.Type))

            nodeMustBeUp(networkMapCache, notary, "TestNotary")
            nodeMustBeUp(networkMapCache, regulator, "Regulator")
            Pair(notary, regulator)
        }
        nodeMustBeDown(notary)
        nodeMustBeDown(regulator)
    }

    @Test
    fun startingNodeWithNoServicesWorks() {
        val noService = driver(quasarJarPath = "../lib/quasar.jar") {
            val noService = startNode("NoService")
            nodeMustBeUp(networkMapCache, noService, "NoService")
            noService
        }
        nodeMustBeDown(noService)
    }

    @Test
    fun randomFreePortAllocationWorks() {
        val nodeInfo = driver(quasarJarPath = "../lib/quasar.jar", portAllocation = PortAllocation.RandomFree()) {
            val nodeInfo = startNode("NoService")
            nodeMustBeUp(networkMapCache, nodeInfo, "NoService")
            nodeInfo
        }
        nodeMustBeDown(nodeInfo)
    }
}
