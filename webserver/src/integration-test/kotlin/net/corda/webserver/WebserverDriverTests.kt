package net.corda.webserver

import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.testing.DUMMY_BANK_A
import net.corda.testing.SerializationEnvironmentRule
import net.corda.testing.driver.WebserverHandle
import net.corda.testing.internal.addressMustBeBound
import net.corda.testing.internal.addressMustNotBeBound
import net.corda.testing.driver.driver
import org.junit.AfterClass
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class WebserverDriverTests {
    companion object {
        private val executorService: ScheduledExecutorService = Executors.newScheduledThreadPool(2)
        @AfterClass
        @JvmStatic
        fun shutdown() {
            executorService.shutdown()
        }

        fun webserverMustBeUp(webserverHandle: WebserverHandle) {
            addressMustBeBound(executorService, webserverHandle.listenAddress, webserverHandle.process)
        }

        fun webserverMustBeDown(webserverAddr: NetworkHostAndPort) {
            addressMustNotBeBound(executorService, webserverAddr)
        }
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    @Test
    fun `starting a node and independent web server works`() {
        val addr = driver {
            val node = startNode(providedName = DUMMY_BANK_A.name).getOrThrow()
            val webserverHandle = startWebserver(node).getOrThrow()
            webserverMustBeUp(webserverHandle)
            webserverHandle.listenAddress
        }
        webserverMustBeDown(addr)
    }
}
