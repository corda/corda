package net.corda.webserver

import net.corda.core.getOrThrow
import net.corda.core.utilities.Authority
import net.corda.testing.DUMMY_BANK_A
import net.corda.testing.driver.WebserverHandle
import net.corda.testing.driver.addressMustBeBound
import net.corda.testing.driver.addressMustNotBeBound
import net.corda.testing.driver.driver
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class DriverTests {
    companion object {
        val executorService: ScheduledExecutorService = Executors.newScheduledThreadPool(2)

        fun webserverMustBeUp(webserverHandle: WebserverHandle) {
            addressMustBeBound(executorService, webserverHandle.listenAddress, webserverHandle.process)
        }

        fun webserverMustBeDown(webserverAddr: Authority) {
            addressMustNotBeBound(executorService, webserverAddr)
        }
    }

    @Test
    fun `starting a node and independent web server works`() {
        val addr = driver {
            val node = startNode(DUMMY_BANK_A.name).getOrThrow()
            val webserverHandle = startWebserver(node).getOrThrow()
            webserverMustBeUp(webserverHandle)
            webserverHandle.listenAddress
        }
        webserverMustBeDown(addr)
    }
}
