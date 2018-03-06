/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.zookeeper

import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.RetryOneTime
import org.apache.curator.test.TestingServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

class PrioritizedLeaderLatchTest {

    private lateinit var zkServer: TestingServer
    private companion object {
        private val ELECTION_PATH = "/example/leader"
    }

    @Before
    fun setup() {
        zkServer = TestingServer(true)
    }

    @After
    fun cleanUp() {
        zkServer.stop()
    }

    @Test
    fun `start stop`() {
        val curatorClient = CuratorFrameworkFactory.newClient(zkServer.connectString, RetryOneTime(100))
        curatorClient.start()
        val latch = PrioritizedLeaderLatch(curatorClient, ELECTION_PATH, "test", 0)
        assertEquals(PrioritizedLeaderLatch.State.CLOSED, latch.state.get())
        try {
            latch.start()
        } catch (e: Exception) {
            fail(e.message)
        }

        assertEquals(PrioritizedLeaderLatch.State.STARTED, latch.state.get())

        try {
            latch.close()
        } catch (e:IOException) {
            fail(e.message)
        }

        assertEquals(PrioritizedLeaderLatch.State.CLOSED, latch.state.get())
        curatorClient.close()
    }

    @Test(expected = IllegalStateException::class)
    fun `double start`() {
        val curatorClient = CuratorFrameworkFactory.newClient(zkServer.connectString, RetryOneTime(100))
        curatorClient.start()
        val latch = PrioritizedLeaderLatch(curatorClient, ELECTION_PATH, "test", 0)
        latch.start()
        latch.start()
    }

    @Test(expected = IllegalStateException::class)
    fun `close while state is closed`() {
        val curatorClient = CuratorFrameworkFactory.newClient(zkServer.connectString, RetryOneTime(100))
        curatorClient.start()
        val latch = PrioritizedLeaderLatch(curatorClient, ELECTION_PATH, "test", 0)
        latch.close()
    }
}