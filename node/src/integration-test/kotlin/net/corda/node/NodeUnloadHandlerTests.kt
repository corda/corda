/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node

import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.toDatabaseSchemaName
import org.junit.Assert
import org.junit.ClassRule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NodeUnloadHandlerTests : IntegrationTest() {
    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(DUMMY_BANK_A_NAME.toDatabaseSchemaName(), DUMMY_NOTARY_NAME.toDatabaseSchemaName() )
        val latch = CountDownLatch(1)
    }

    @Test
    fun `should be able to register run on stop lambda`() {
        driver(DriverParameters(startNodesInProcess = true, extraCordappPackagesToScan = listOf("net.corda.node"))) {
            startNode(providedName = DUMMY_BANK_A_NAME).getOrThrow()
            // just want to fall off the end of this for the mo...
        }
        Assert.assertTrue("Timed out waiting for AbstractNode to invoke the test service shutdown callback", latch.await(30, TimeUnit.SECONDS))
    }

    @CordaService
    class RunOnStopTestService(serviceHub: ServiceHub) : SingletonSerializeAsToken() {

        companion object {
            private val log = contextLogger()
        }

        init {
            serviceHub.registerUnloadHandler(this::shutdown)
        }

        fun shutdown() {
            log.info("shutting down")
            latch.countDown()
        }
    }
}