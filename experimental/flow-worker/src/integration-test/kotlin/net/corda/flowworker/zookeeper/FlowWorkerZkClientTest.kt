package net.corda.flowworker.zookeeper

import net.corda.testing.core.SerializationEnvironmentRule
import org.apache.curator.test.TestingServer
import org.apache.curator.utils.ZKPaths
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.assertEquals

class FlowWorkerZkClientTest {
    @Rule
    @JvmField
    val serializationEnvironment = SerializationEnvironmentRule(true)
    private lateinit var zkServer: TestingServer

    private companion object {
        private val ELECTION_PATH = "/example/leader"
        private val FLOW_BUCKETS_PATH = "/example/flowBuckets"
    }

    @Before
    fun setup() {
        zkServer = TestingServer(59980, true)
    }

    @After
    fun cleanUp() {
        zkServer.stop()
    }

    @Test
    fun `client with highest priority becomes leader`() {
        println(zkServer.connectString)
        val alice = FlowWorkerZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test5"), ZKPaths.makePath(FLOW_BUCKETS_PATH, "test5"), "ALICE", 0)
        val bob = FlowWorkerZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test5"), ZKPaths.makePath(FLOW_BUCKETS_PATH, "test5"), "BOB", 1)
        val chip = FlowWorkerZkClient(zkServer.connectString, ZKPaths.makePath(ELECTION_PATH, "test5"), ZKPaths.makePath(FLOW_BUCKETS_PATH, "test5"), "CHIP", 2)
        val aliceLeaderGain = CountDownLatch(1)
        val bobLeaderGain = CountDownLatch(1)
        val bobLeaderLoss = CountDownLatch(1)
        val chipLeaderLoss = CountDownLatch(1)
        val chipLeaderGain = CountDownLatch(1)

        listOf(alice, bob, chip).forEach { client ->
            thread {
                when (client) {
                    alice -> client.registration.subscribe { if (it.isLeader) aliceLeaderGain.countDown() }
                    bob -> client.registration.subscribe { if (it.isLeader) bobLeaderGain.countDown() else bobLeaderLoss.countDown() }
                    chip -> client.registration.subscribe { if (it.isLeader) chipLeaderGain.countDown() else chipLeaderLoss.countDown() }
                }
                client.start()
            }
        }

        aliceLeaderGain.await()
        require(alice.isLeader())
        assertFalse(bob.isLeader())
        assertFalse(chip.isLeader())

        while (alice.partition.value?.first != 0L) {
            Thread.sleep(1000)
        }

        assertEquals(0L to Long.MAX_VALUE / 3, alice.partition.value)
        assertEquals(Long.MAX_VALUE / 3 + 1 to Long.MAX_VALUE / 3 * 2, bob.partition.value)
        assertEquals(Long.MAX_VALUE / 3 * 2 + 1 to Long.MAX_VALUE, chip.partition.value)

        alice.close()
        bobLeaderGain.await()
        require(bob.isLeader())

        while (bob.partition.value?.first != 0L) {
            Thread.sleep(1000)
        }

        assertEquals(0L to Long.MAX_VALUE / 2, bob.partition.value)
        assertEquals(Long.MAX_VALUE / 2 + 1 to Long.MAX_VALUE, chip.partition.value)

        bob.close()
        chipLeaderGain.await()
        require(chip.isLeader())

        while (chip.partition.value?.first != 0L) {
            Thread.sleep(1000)
        }

        assertEquals(0L to Long.MAX_VALUE, chip.partition.value)
        chip.close()
    }
}