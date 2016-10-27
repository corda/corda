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
        fun nodeMustBeUp(nodeInfo: NodeInfo, nodeName: String) {
            val hostAndPort = ArtemisMessagingComponent.toHostAndPort(nodeInfo.address)
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

            nodeMustBeUp(notary.get().nodeInfo, "TestNotary")
            nodeMustBeUp(regulator.get().nodeInfo, "Regulator")
            Pair(notary.get(), regulator.get())
        }
        nodeMustBeDown(notary.nodeInfo)
        nodeMustBeDown(regulator.nodeInfo)
    }

    @Test
    fun startingNodeWithNoServicesWorks() {
        val noService = driver {
            val noService = startNode("NoService")
            nodeMustBeUp(noService.get().nodeInfo, "NoService")
            noService.get()
        }
        nodeMustBeDown(noService.nodeInfo)
    }

    @Test
    fun randomFreePortAllocationWorks() {
        val nodeInfo = driver(portAllocation = PortAllocation.RandomFree()) {
            val nodeInfo = startNode("NoService")
            nodeMustBeUp(nodeInfo.get().nodeInfo, "NoService")
            nodeInfo.get()
        }
        nodeMustBeDown(nodeInfo.nodeInfo)
    }
}
