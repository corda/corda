package net.corda.webserver

import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.testing.DUMMY_BANK_A_NAME
import net.corda.testing.IntegrationTest
import net.corda.testing.IntegrationTestSchemas
import net.corda.testing.driver.WebserverHandle
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.addressMustBeBound
import net.corda.testing.node.internal.addressMustNotBeBound
import net.corda.testing.toDatabaseSchemaName
import org.junit.ClassRule
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class WebserverDriverTests : IntegrationTest() {
    companion object {
        val executorService: ScheduledExecutorService = Executors.newScheduledThreadPool(2)

        fun webserverMustBeUp(webserverHandle: WebserverHandle) {
            addressMustBeBound(executorService, webserverHandle.listenAddress, webserverHandle.process)
        }

        fun webserverMustBeDown(webserverAddr: NetworkHostAndPort) {
            addressMustNotBeBound(executorService, webserverAddr)
        }

        @ClassRule @JvmField
        val databaseSchemas = IntegrationTestSchemas(DUMMY_BANK_A_NAME.toDatabaseSchemaName())
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
