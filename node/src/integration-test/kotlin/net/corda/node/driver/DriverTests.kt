package net.corda.node.driver

import com.google.common.net.HostAndPort
import net.corda.core.getOrThrow
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.ServiceInfo
import net.corda.node.services.api.RegulatorService
import net.corda.node.services.messaging.ArtemisMessagingComponent
import net.corda.node.services.transactions.SimpleNotaryService
import org.junit.Test
import java.util.concurrent.Executors


class DriverTests {
    companion object {
        val executorService = Executors.newScheduledThreadPool(2)

        fun nodeMustBeUp(nodeInfo: NodeInfo) {
            val hostAndPort = ArtemisMessagingComponent.toHostAndPort(nodeInfo.address)
            // Check that the port is bound
            addressMustBeBound(executorService, hostAndPort)
        }

        fun nodeMustBeDown(nodeInfo: NodeInfo) {
            val hostAndPort = ArtemisMessagingComponent.toHostAndPort(nodeInfo.address)
            // Check that the port is bound
            addressMustNotBeBound(executorService, hostAndPort)
        }

        fun webserverMustBeUp(webserverAddr: HostAndPort) {
            addressMustBeBound(executorService, webserverAddr)
        }

        fun webserverMustBeDown(webserverAddr: HostAndPort) {
            addressMustNotBeBound(executorService, webserverAddr)
        }
    }

    @Test
    fun simpleNodeStartupShutdownWorks() {
        val (notary, regulator) = driver {
            val notary = startNode("TestNotary", setOf(ServiceInfo(SimpleNotaryService.type)))
            val regulator = startNode("Regulator", setOf(ServiceInfo(RegulatorService.type)))

            nodeMustBeUp(notary.getOrThrow().nodeInfo)
            nodeMustBeUp(regulator.getOrThrow().nodeInfo)
            Pair(notary.getOrThrow(), regulator.getOrThrow())
        }
        nodeMustBeDown(notary.nodeInfo)
        nodeMustBeDown(regulator.nodeInfo)
    }

    @Test
    fun startingNodeWithNoServicesWorks() {
        val noService = driver {
            val noService = startNode("NoService")
            nodeMustBeUp(noService.getOrThrow().nodeInfo)
            noService.getOrThrow()
        }
        nodeMustBeDown(noService.nodeInfo)
    }

    @Test
    fun randomFreePortAllocationWorks() {
        val nodeInfo = driver(portAllocation = PortAllocation.RandomFree) {
            val nodeInfo = startNode("NoService")
            nodeMustBeUp(nodeInfo.getOrThrow().nodeInfo)
            nodeInfo.getOrThrow()
        }
        nodeMustBeDown(nodeInfo.nodeInfo)
    }

    @Test
    fun `starting a node and independent web server works`() {
        val addr = driver {
            val node = startNode("test").getOrThrow()
            val webserverAddr = startWebserver(node).getOrThrow()
            webserverMustBeUp(webserverAddr)
            webserverAddr
        }
        webserverMustBeDown(addr)
    }
}
