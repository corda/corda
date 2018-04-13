/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.webserver

import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.driver.WebserverHandle
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.addressMustBeBound
import net.corda.testing.node.internal.addressMustNotBeBound
import net.corda.testing.internal.toDatabaseSchemaName
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

        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(DUMMY_BANK_A_NAME.toDatabaseSchemaName(), DUMMY_NOTARY_NAME.toDatabaseSchemaName())
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
