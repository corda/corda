/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */
package net.corda.node.internal

import net.corda.core.internal.packageName
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.contextLogger
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.cordappsForPackages
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NodeUnloadHandlerTests {
    companion object {
        val registerLatch = CountDownLatch(1)
        val shutdownLatch = CountDownLatch(1)
    }

    private val mockNet = InternalMockNetwork(cordappsForAllNodes = cordappsForPackages(javaClass.packageName), notarySpecs = emptyList())

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `should be able to register run on stop lambda`() {
        val node = mockNet.createNode()
        registerLatch.await()  // Make sure the handler is registered on node start up
        node.dispose()
        assertTrue("Timed out waiting for AbstractNode to invoke the test service shutdown callback", shutdownLatch.await(30, TimeUnit.SECONDS))
    }

    @Suppress("unused")
    @CordaService
    class RunOnStopTestService(serviceHub: ServiceHub) : SingletonSerializeAsToken() {
        companion object {
            private val log = contextLogger()
        }

        init {
            serviceHub.registerUnloadHandler(this::shutdown)
            registerLatch.countDown()
        }

        private fun shutdown() {
            log.info("shutting down")
            shutdownLatch.countDown()
        }
    }
}
