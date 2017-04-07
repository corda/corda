package net.corda.webserver

import com.google.common.net.HostAndPort
import net.corda.core.getOrThrow
import net.corda.core.utilities.DUMMY_BANK_A
import net.corda.node.driver.addressMustBeBound
import net.corda.node.driver.addressMustNotBeBound
import net.corda.node.driver.driver
import org.junit.Test
import java.util.concurrent.Executors

class DriverTests {
    companion object {
        val executorService = Executors.newScheduledThreadPool(2)

        fun webserverMustBeUp(webserverAddr: HostAndPort) {
            addressMustBeBound(executorService, webserverAddr)
        }

        fun webserverMustBeDown(webserverAddr: HostAndPort) {
            addressMustNotBeBound(executorService, webserverAddr)
        }
    }

    @Test
    fun `starting a node and independent web server works`() {
        val addr = driver {
            val node = startNode(DUMMY_BANK_A.name).getOrThrow()
            val webserverAddr = startWebserver(node).getOrThrow()
            webserverMustBeUp(webserverAddr)
            webserverAddr
        }
        webserverMustBeDown(addr)
    }
}
