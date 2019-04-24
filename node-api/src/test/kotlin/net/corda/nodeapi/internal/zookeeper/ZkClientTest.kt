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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class ZkClientTests {

    private lateinit var zkServer: TestingServer
    private companion object {
        private const val ELECTION_PATH = "/example/leader"
        private const val ELECTION_TIMEOUT = 2000L
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
        val leaderCount = AtomicInteger()
        val failures = mutableListOf<String>()

        thread {
            client.start()
            client.addLeadershipListener(SyncHelperListener("test", leaderCount, failures, leaderGain))
            client.requestLeadership()
        }

        leaderGain.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        require(client.isLeader())
        validateElectionResults(leaderCount, failures)
        client.close()
    }

    @Test
    fun `single client relinquishes leadership`() {
        val client = ZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test4"), "test", 0)
        val leaderGain = CountDownLatch(1)
        val leaderLoss = CountDownLatch(1)
        val leaderCount = AtomicInteger()
        val failures = mutableListOf<String>()

        thread {
            client.start()
            client.addLeadershipListener(SyncHelperListener("test",  leaderCount, failures, leaderGain, leaderLoss))
            client.requestLeadership()
        }

        leaderGain.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        require(client.isLeader())
        client.relinquishLeadership()
        leaderLoss.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        assertFalse(client.isLeader())
        validateElectionResults(leaderCount, mutableListOf())

        client.close()
    }

    @Test
    fun `client with highest priority becomes leader`() {
        val alice = ZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test5"), "ALICE", 0)
        val bob = ZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test5"), "BOB", 1)
        val chip = ZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test5"), "CHIP", 2)
        val aliceLeaderGain = CountDownLatch(1)
        val leaderCount = AtomicInteger()
        val failures = mutableListOf<String>()

        listOf(alice, bob, chip).forEach { client ->
            thread{
                client.start()
                when (client) {
                    alice -> client.addLeadershipListener(SyncHelperListener(client.nodeId, leaderCount, failures, aliceLeaderGain))
                    bob -> client.addLeadershipListener(SyncHelperListener(client.nodeId, leaderCount, failures))
                    chip -> client.addLeadershipListener(SyncHelperListener(client.nodeId, leaderCount, failures))
                }
                client.requestLeadership()
            }
        }

        aliceLeaderGain.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        require(alice.isLeader())
        assertFalse(bob.isLeader())
        assertFalse(chip.isLeader())
        validateElectionResults(leaderCount, failures)
        listOf(alice, bob, chip).forEach { client -> client.close() }
    }

    @Test
    fun `leader relinquishes, next highest priority takes over`() {
        val alice = ZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test6"), "ALICE", 0)
        val bob = ZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test6"), "BOB", 1)
        val chip = ZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test6"), "CHIP", 2)
        val aliceLeaderGain = CountDownLatch(1)
        val bobLeaderGain  = CountDownLatch(1)
        val chipLeaderGain = CountDownLatch(1)
        val leaderCount = AtomicInteger()
        val failures = mutableListOf<String>()

        // This is necessary to ensure that correct start-up order is enforced.
        val aliceLeadershipRequested = CountDownLatch(1)

        listOf(chip, alice, bob).map { client ->
            thread {
                client.start()
                when (client) {
                    alice -> {
                        client.addLeadershipListener(SyncHelperListener(client.nodeId, leaderCount, failures, aliceLeaderGain))
                        alice.requestLeadership()
                        aliceLeadershipRequested.countDown()
                    }

                    bob -> {
                        bob.addLeadershipListener(SyncHelperListener(client.nodeId, leaderCount, failures, bobLeaderGain))
                        aliceLeadershipRequested.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)
                        bob.requestLeadership()
                    }

                    chip -> {
                        chip.addLeadershipListener(SyncHelperListener(client.nodeId, leaderCount, failures, chipLeaderGain))
                        aliceLeadershipRequested.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)
                        chip.requestLeadership()
                    }
                }
            }
        }.forEach { it.join() }

        aliceLeaderGain.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        require(alice.isLeader())
        assertFalse(bob.isLeader())
        assertFalse(chip.isLeader())

        alice.relinquishLeadership()
        bobLeaderGain.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS) // wait for bob to become leader

        assertFalse(alice.isLeader())
        require(bob.isLeader())
        assertFalse(chip.isLeader())

        bob.relinquishLeadership()
        chipLeaderGain.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)

        assertFalse(alice.isLeader())
        assertFalse(bob.isLeader())
        require(chip.isLeader())
        validateElectionResults(leaderCount, failures)
        listOf(alice, bob, chip).forEach { client -> client.close() }
    }

    @Test
    fun `clients with higher priority join and take leadership`() {
        val alice = ZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test7"), "ALICE", 0)
        val bob = ZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test7"), "BOB", 50) // Use numbers that check for numeric sorting
        val chip = ZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test7"), "CHIP", 2000)
        val aliceLeaderGain = CountDownLatch(1)
        val bobLeaderGain  = CountDownLatch(1)
        val chipLeaderGain = CountDownLatch(1)
        val leaderCount = AtomicInteger()
        val failures = mutableListOf<String>()

        chip.start()
        chip.addLeadershipListener(SyncHelperListener(chip.nodeId, leaderCount, failures, chipLeaderGain))
        chip.requestLeadership()

        chipLeaderGain.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        require(chip.isLeader())

        bob.start()
        bob.addLeadershipListener(SyncHelperListener(bob.nodeId, leaderCount, failures, bobLeaderGain))
        bob.requestLeadership()

        bobLeaderGain.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        require(bob.isLeader())
        assertFalse(chip.isLeader())

        alice.start()
        alice.addLeadershipListener(SyncHelperListener(alice.nodeId, leaderCount, failures, aliceLeaderGain))
        alice.requestLeadership()

        aliceLeaderGain.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)

        require(alice.isLeader())
        assertFalse(bob.isLeader())
        assertFalse(chip.isLeader())
        validateElectionResults(leaderCount, failures)
        listOf(alice, bob, chip).forEach { client -> client.close() }
    }

    @Test
    fun `client with mid-level priority joins and becomes leader after current leader relinquishes`() {
        val alice = ZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test8"), "ALICE", 0)
        val bob = ZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test8"), "BOB", 1)
        val chip = ZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test8"), "CHIP", 2)
        val aliceLeaderGain = CountDownLatch(1)
        val bobLeaderGain  = CountDownLatch(1)
        val chipLeaderGain = CountDownLatch(1)
        val leaderCount = AtomicInteger()
        val failures = mutableListOf<String>()

        listOf(alice, chip).forEach { client ->
            thread{
                client.start()
                when (client) {
                    alice -> client.addLeadershipListener(SyncHelperListener(client.nodeId, leaderCount, failures, aliceLeaderGain))
                    chip -> client.addLeadershipListener(SyncHelperListener(client.nodeId, leaderCount, failures, chipLeaderGain))
                }
                client.requestLeadership()
            }
        }

        aliceLeaderGain.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)
        require(alice.isLeader())
        assertFalse(chip.isLeader())

        bob.start()
        bob.addLeadershipListener(SyncHelperListener(bob.nodeId, leaderCount, failures, bobLeaderGain))
        bob.requestLeadership()

        require(alice.isLeader())
        assertFalse(bob.isLeader())
        assertFalse(chip.isLeader())

        alice.relinquishLeadership()
        bobLeaderGain.await(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)

        assertFalse(alice.isLeader())
        require(bob.isLeader())
        assertFalse(chip.isLeader())
        validateElectionResults(leaderCount, failures)
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
        val leaderCount = AtomicInteger()
        val failures = mutableListOf<String>()
        clientList.forEach { client ->
            thread{
                client.addLeadershipListener(SyncHelperListener(client.nodeId, leaderCount, failures))
                client.start()
                val randomizer = Random()
                val actions = listOf(Action.RELINQUISH, Action.REQUEST)
                for (i in 1..ACTIONS_NUMBER) {
                    val action = actions[randomizer.nextInt(actions.size)]
                    when(action) {
                        Action.REQUEST ->  client.requestLeadership()
                        Action.RELINQUISH ->  client.relinquishLeadership()
                    }
                }
                countDownLatch.countDown()
            }
        }
        countDownLatch.await(CLIENT_TIMEOUT, TimeUnit.SECONDS)
        Thread.sleep(1000) // Wait a bit for curator threads to finish their work
        validateElectionResults(leaderCount, failures)

        clientList.forEach { client -> client.close() }
    }

    private fun validateElectionResults(leaderCount: AtomicInteger, failures: MutableList<String>) {
        require(leaderCount.get() <= 1)
        if (failures.size != 0) {
            failures.forEach {
                log.error(it)
            }
            assert(failures.isNotEmpty())
        }
    }

    private enum class Action {
        REQUEST, RELINQUISH
    }

    private class SyncHelperListener(private val nodeId: String,
                                     private val leaderCount: AtomicInteger,
                                     private val failures: MutableList<String>,
                                     private val leaderGain: CountDownLatch = CountDownLatch(1),
                                     private val leaderLoss: CountDownLatch = CountDownLatch(1)
                                     ) : CordaLeaderListener {
        override fun notLeader() {
            log.info("$nodeId is no longer leader.")
            val previousCount = leaderCount.getAndDecrement()
            if (previousCount != 1) {
                failures.add("LeaderCount expected was 1. Was $previousCount.")
            }
            leaderLoss.countDown()
        }
        override fun isLeader() {
            log.info("$nodeId is the new leader.")
            val previousCount = leaderCount.getAndIncrement()
            if (previousCount != 0) {
                failures.add("LeaderCount expected was 0. Was $previousCount")
            }
            leaderGain.countDown()
        }
    }
}