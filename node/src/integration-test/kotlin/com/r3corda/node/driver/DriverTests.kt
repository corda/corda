package com.r3corda.node.driver

import com.r3corda.core.node.NodeInfo
import com.r3corda.core.node.services.NetworkMapCache
import com.r3corda.core.node.services.ServiceInfo
import com.r3corda.node.services.api.RegulatorService
import com.r3corda.node.services.messaging.ArtemisMessagingComponent
import com.r3corda.node.services.transactions.SimpleNotaryService
import org.junit.Test


class DriverTests {
    companion object {
        fun nodeMustBeUp(networkMapCache: NetworkMapCache, nodeInfo: NodeInfo, nodeName: String) {
            val hostAndPort = ArtemisMessagingComponent.toHostAndPort(nodeInfo.address)
            // Check that the node is registered in the network map
            poll("network map cache for $nodeName") {
                networkMapCache.get().firstOrNull {
                    it.legalIdentity.name == nodeName
                }
            }
            // Check that the port is bound
            addressMustBeBound(hostAndPort)
        }

        fun nodeMustBeDown(nodeInfo: NodeInfo) {
            val hostAndPort = ArtemisMessagingComponent.toHostAndPort(nodeInfo.address)
            // Check that the port is bound
            addressMustNotBeBound(hostAndPort)
        }
    }

    @Test
    fun simpleNodeStartupShutdownWorks() {
        val (notary, regulator) = driver {
            val notary = startNode("TestNotary", setOf(ServiceInfo(SimpleNotaryService.type)))
            val regulator = startNode("Regulator", setOf(ServiceInfo(RegulatorService.type)))

            nodeMustBeUp(networkMapCache, notary.get(), "TestNotary")
            nodeMustBeUp(networkMapCache, regulator.get(), "Regulator")
            Pair(notary.get(), regulator.get())
        }
        nodeMustBeDown(notary)
        nodeMustBeDown(regulator)
    }

    @Test
    fun startingNodeWithNoServicesWorks() {
        val noService = driver {
            val noService = startNode("NoService")
            nodeMustBeUp(networkMapCache, noService.get(), "NoService")
            noService.get()
        }
        nodeMustBeDown(noService)
    }

    @Test
    fun randomFreePortAllocationWorks() {
        val nodeInfo = driver(portAllocation = PortAllocation.RandomFree()) {
            val nodeInfo = startNode("NoService")
            nodeMustBeUp(networkMapCache, nodeInfo.get(), "NoService")
            nodeInfo.get()
        }
        nodeMustBeDown(nodeInfo)
    }
}
