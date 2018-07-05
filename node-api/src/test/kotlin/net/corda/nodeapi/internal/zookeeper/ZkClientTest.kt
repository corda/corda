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

import net.corda.core.utilities.contextLogger
import org.apache.curator.test.TestingServer
import org.apache.curator.utils.ZKPaths
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.assertEquals

class ZkClientTests {

    private lateinit var zkServer: TestingServer
    private companion object {
        private val ELECTION_PATH = "/example/leader"
        private val ELECTION_TIMEOUT = 2000L
        private val log = contextLogger()

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
    fun `start and stop client`() {
        val client = ZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test1"), "test", 0)
        client.start()
        assertFalse(client.isLeader())
        client.close()
    }

    @Test(expected = IllegalStateException::class)
    fun `client requests leader before start`() {
        val client = ZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test2"), "test", 0)
        client.requestLeadership()
    }

    @Test
    fun `single client becomes leader`() {
        val client = ZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test3"), "test", 0)
        val leaderGain = CountDownLatch(1)

        thread {
            client.start()
            client.addLeadershipListener(SyncHelperListener("test", leaderGain))
            client.requestLeadership()
        }

        leaderGain.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        require(client.isLeader())
        client.close()
    }

    @Test
    fun `single client relinquishes leadership`() {
        val client = ZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test4"), "test", 0)
        val leaderGain = CountDownLatch(1)
        val leaderLoss = CountDownLatch(1)

        thread {
            client.start()
            client.addLeadershipListener(SyncHelperListener("test", leaderGain, leaderLoss))
            client.requestLeadership()
        }

        leaderGain.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        require(client.isLeader())
        client.relinquishLeadership()
        leaderLoss.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        assertFalse(client.isLeader())
        client.close()
    }

    @Test
    fun `client with highest priority becomes leader`() {
        val alice = ZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test5"), "ALICE", 0)
        val bob = ZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test5"), "BOB", 1)
        val chip = ZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test5"), "CHIP", 2)
        val aliceLeaderGain = CountDownLatch(1)
        val bobLeaderGain  = CountDownLatch(1)
        val bobLeaderLoss = CountDownLatch(1)
        val chipLeaderLoss = CountDownLatch(1)
        val chipLeaderGain = CountDownLatch(1)

        listOf(alice, bob, chip).forEach { client ->
            thread{
                client.start()
                when (client) {
                    alice -> client.addLeadershipListener(SyncHelperListener(client.nodeId, aliceLeaderGain))
                    bob -> client.addLeadershipListener(SyncHelperListener(client.nodeId, bobLeaderGain, bobLeaderLoss))
                    chip -> client.addLeadershipListener(SyncHelperListener(client.nodeId, chipLeaderGain, chipLeaderLoss))
                }
                client.requestLeadership()
            }
        }

        aliceLeaderGain.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        require(alice.isLeader())
        if (bobLeaderGain.count == 0L) //wait to lose leadership if leader at some point
            bobLeaderLoss.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        assertFalse(bob.isLeader())
        if (chipLeaderGain.count == 0L) //wait to lose leadership if leader at some point
            chipLeaderLoss.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        assertFalse(chip.isLeader())

        listOf(alice, bob, chip).forEach { client -> client.close() }
    }

    @Test
    fun `leader relinquishes, next highest priority takes over`() {
        val alice = ZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test6"), "ALICE", 0)
        val bob = ZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test6"), "BOB", 1)
        val chip = ZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test6"), "CHIP", 2)
        val aliceLeaderGain = CountDownLatch(1)
        val bobLeaderGain  = CountDownLatch(1)
        val bobLeaderLoss = CountDownLatch(1)
        val chipLeaderLoss = CountDownLatch(1)
        val chipLeaderGain = CountDownLatch(1)

        listOf(alice, bob, chip).forEach { client ->
            thread{
                client.start()
                when (client) {
                    alice -> client.addLeadershipListener(SyncHelperListener(client.nodeId, aliceLeaderGain))
                    bob -> client.addLeadershipListener(SyncHelperListener(client.nodeId, bobLeaderGain, bobLeaderLoss))
                    chip -> client.addLeadershipListener(SyncHelperListener(client.nodeId, chipLeaderGain, chipLeaderLoss))
                }
                client.requestLeadership()
            }
        }

        aliceLeaderGain.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        require(alice.isLeader())
        if (bobLeaderGain.count == 0L) //wait to lose leadership if leader at some point
            bobLeaderLoss.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        assertFalse(bob.isLeader())
        if (chipLeaderGain.count == 0L)
            chipLeaderLoss.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        assertFalse(chip.isLeader()) //wait to lose leadership if leader at some point

        val bobLeaderGain2 = CountDownLatch(1)
        bob.addLeadershipListener(SyncHelperListener(bob.nodeId, bobLeaderGain2))

        alice.relinquishLeadership()
        bobLeaderGain2.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS) // wait for bob to become leader

        assertFalse(alice.isLeader())
        require(bob.isLeader())
        assertFalse(chip.isLeader())

        val chipLeaderGain2 = CountDownLatch(1)
        chip.addLeadershipListener(SyncHelperListener(chip.nodeId, chipLeaderGain2))

        bob.relinquishLeadership()
        chipLeaderGain2.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)

        assertFalse(alice.isLeader())
        assertFalse(bob.isLeader())
        require(chip.isLeader())

        listOf(alice, bob, chip).forEach { client -> client.close() }
    }

    @Test
    fun `clients with higher priority join and take leadership`() {
        val alice = ZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test7"), "ALICE", 0)
        val bob = ZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test7"), "BOB", 50) // Use numbers that check for numeric sorting
        val chip = ZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test7"), "CHIP", 2000)
        val aliceLeaderGain = CountDownLatch(1)
        val bobLeaderGain  = CountDownLatch(1)
        val bobLeaderLoss = CountDownLatch(1)
        val chipLeaderLoss = CountDownLatch(1)
        val chipLeaderGain = CountDownLatch(1)

        chip.start()
        chip.addLeadershipListener(SyncHelperListener(chip.nodeId, chipLeaderGain, chipLeaderLoss))
        chip.requestLeadership()

        chipLeaderGain.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        require(chip.isLeader())

        bob.start()
        bob.addLeadershipListener(SyncHelperListener(bob.nodeId, bobLeaderGain, bobLeaderLoss))
        bob.requestLeadership()

        chipLeaderLoss.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        bobLeaderGain.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        require(bob.isLeader())
        assertFalse(chip.isLeader())

        alice.start()
        alice.addLeadershipListener(SyncHelperListener(alice.nodeId, aliceLeaderGain))
        alice.requestLeadership()

        bobLeaderLoss.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        aliceLeaderGain.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)

        require(alice.isLeader())
        assertFalse(bob.isLeader())
        assertFalse(chip.isLeader())

        listOf(alice, bob, chip).forEach { client -> client.close() }
    }

    @Test
    fun `client with mid-level priority joins and becomes leader after current leader relinquishes`() {
        val alice = ZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test8"), "ALICE", 0)
        val bob = ZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test8"), "BOB", 1)
        val chip = ZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test8"), "CHIP", 2)
        val aliceLeaderGain = CountDownLatch(1)
        val aliceLeaderLoss = CountDownLatch(1)
        val bobLeaderGain  = CountDownLatch(1)
        val chipLeaderLoss = CountDownLatch(1)
        val chipLeaderGain = CountDownLatch(1)

        listOf(alice, chip).forEach { client ->
            thread{
                client.start()
                when (client) {
                    alice -> client.addLeadershipListener(SyncHelperListener(client.nodeId, aliceLeaderGain, aliceLeaderLoss))
                    chip -> client.addLeadershipListener(SyncHelperListener(client.nodeId, chipLeaderGain, chipLeaderLoss))
                }
                client.requestLeadership()
            }
        }

        aliceLeaderGain.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        if (chipLeaderGain.count == 0L) //wait to lose leadership if leader at some point
            chipLeaderLoss.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        require(alice.isLeader())
        assertFalse(chip.isLeader())

        bob.start()
        bob.addLeadershipListener(SyncHelperListener(bob.nodeId, bobLeaderGain))
        bob.requestLeadership()

        require(alice.isLeader())
        assertFalse(bob.isLeader())
        assertFalse(chip.isLeader())

        val chipLeaderGain2 = CountDownLatch(1)
        val chipLeaderLoss2 = CountDownLatch(1)
        chip.addLeadershipListener(SyncHelperListener(chip.nodeId, chipLeaderGain2, chipLeaderLoss2))
        alice.relinquishLeadership()
        aliceLeaderLoss.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        bobLeaderGain.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        if (chipLeaderGain.count == 0L) //wait to lose leadership if gained
            chipLeaderLoss2.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)

        assertFalse(alice.isLeader())
        require(bob.isLeader())
        assertFalse(chip.isLeader())

        listOf(alice, bob, chip).forEach { client -> client.close() }
    }

    @Test
    fun `clients randomly do things`() {
        val CLIENTS_NUMBER = 4
        val ACTIONS_NUMBER = 100
        val CLIENT_TIMEOUT = 60L

        val clientList = mutableListOf<ZkClient>()
        (1..CLIENTS_NUMBER).forEach {
            clientList.add(ZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test9"), "CLI_${it}", it))
        }

        val countDownLatch = CountDownLatch(clientList.size)
        val leaderBuffer = mutableListOf<String>()

        clientList.forEach { client ->
            thread{
                client.addLeadershipListener(HelperListener(client.nodeId, leaderBuffer))
                client.start()
                val randomizer = Random()
                val actions = listOf(Action.RELINQUISH, Action.REQUEST)
                for (i in 1..ACTIONS_NUMBER) {
                    val action = actions[randomizer.nextInt(actions.size)]
                    when(action) {
                        Action.REQUEST ->  client.requestLeadership()
                        Action.RELINQUISH ->  client.relinquishLeadership()
                        else -> throw IllegalArgumentException("Invalid action choice")
                    }
                    Thread.sleep(100)
                }

                countDownLatch.countDown()
            }
        }

        countDownLatch.await(CLIENT_TIMEOUT, TimeUnit.SECONDS)
        //only one leader should exist
        var leaderCount = 0
        var leaderId = ""

        clientList.forEach { client ->
            if (client.isLeader()) {
                leaderCount++
                leaderId = client.nodeId
            }
        }

        require(leaderCount <= 1)
        require(leaderBuffer.size <= 1)
        if (leaderBuffer.size == 1) {
            println(leaderBuffer)
            assertEquals(leaderBuffer.first(), leaderId)
        }

        clientList.forEach { client -> client.close() }
    }

    private enum class Action {
        START, STOP, REQUEST, RELINQUISH
    }

    private class HelperListener(private val nodeId: String,
                                 private val leaders: MutableList<String>) : CordaLeaderListener {
        @Synchronized
        override fun notLeader() {
            leaders.remove(nodeId)
        }

        @Synchronized
        override fun isLeader() {
            leaders.add(nodeId)
        }
    }
    private class SyncHelperListener(private val nodeId: String,
                                     private val leaderGain: CountDownLatch = CountDownLatch(1),
                                     private val leaderLoss: CountDownLatch = CountDownLatch(1)) : CordaLeaderListener {
        override fun notLeader() {
            log.info("$nodeId is no longer leader.")
            leaderLoss.countDown()

        }
        override fun isLeader() {
            log.info("$nodeId is the new leader.")
            leaderGain.countDown()
        }
    }
}