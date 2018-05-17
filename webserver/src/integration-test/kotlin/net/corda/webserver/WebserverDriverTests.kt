@file:Suppress("DEPRECATION")

package net.corda.webserver

import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.driver.WebserverHandle
import net.corda.testing.node.internal.addressMustBeBound
import net.corda.testing.node.internal.addressMustNotBeBound
import net.corda.testing.driver.driver
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class WebserverDriverTests {
    companion object {
        private val executorService: ScheduledExecutorService = Executors.newScheduledThreadPool(2)

        fun webserverMustBeUp(webserverHandle: WebserverHandle) {
            addressMustBeBound(executorService, webserverHandle.listenAddress, webserverHandle.process)
        }

        fun webserverMustBeDown(webserverAddr: NetworkHostAndPort) {
            addressMustNotBeBound(executorService, webserverAddr)
        }
    }

    @Test
    fun `starting a node and independent web server works`() {
        val addr = driver {
            val node = startNode(providedName = DUMMY_BANK_A_NAME).getOrThrow()
            val webserverHandle = startWebserver(node).getOrThrow()
            webserverMustBeUp(webserverHandle)
            webserverHandle.listenAddress
        }
        webserverMustBeDown(addr)
    }
}
