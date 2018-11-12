package net.corda.nodeapi.internal.bully

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.services.config.EnterpriseConfiguration
import net.corda.node.services.config.MutualExclusionConfiguration
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.configureWithDevSSLCertificate
import net.corda.node.services.messaging.ArtemisMessagingServer
import net.corda.nodeapi.internal.ArtemisMessagingClient
import net.corda.nodeapi.internal.ArtemisSessionProvider
import net.corda.nodeapi.internal.config.MessagingServerConnectionConfiguration
import net.corda.nodeapi.internal.zookeeper.CordaLeaderListener
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.MAX_MESSAGE_SIZE
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.internal.rigorousMock
import net.corda.testing.internal.stubs.CertificateStoreStubs
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BullyLeaderTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Rule
    @JvmField
    val serializationEnvironment = SerializationEnvironmentRule(true)

    private abstract class AbstractNodeConfiguration : NodeConfiguration

    class StateTracker(val name: String,
                       val counts: AtomicInteger,
                       val failureRef: AtomicBoolean) : CordaLeaderListener {
        private val monitor = Object()
        var isLeader: Boolean = false

        override fun isLeader() {
            println("$name is leader")
            val count = counts.getAndIncrement()
            if (count > 0) {
                println("More than one leader at once")
                failureRef.set(true)
            }
            synchronized(monitor) {
                isLeader = true
                monitor.notifyAll()
            }
        }

        override fun notLeader() {
            println("$name lost leadership")
            counts.decrementAndGet()
            synchronized(monitor) {
                isLeader = false
                monitor.notifyAll()
            }
        }

        fun waitUp() {
            synchronized(monitor) {
                while (!isLeader) {
                    monitor.wait()
                }
            }
        }

        fun waitDown() {
            synchronized(monitor) {
                while (isLeader) {
                    monitor.wait()
                }
            }
        }
    }

    @Test
    fun `Simple Leader Test`() {
        val artemisServer = createArtemisServer()
        artemisServer.use {
            val artemisClient = createArtemisClient()
            try {
                val counts = AtomicInteger(0)
                val errors = AtomicBoolean(false)
                val tracker = StateTracker("1", counts, errors)
                val leader = BullyLeaderClient(artemisClient, "test", "1", 10)
                leader.addLeadershipListener(tracker)
                assertFalse(leader.isStarted())
                assertFalse(leader.isLeader())
                leader.start()
                assertTrue(leader.isStarted())
                assertFalse(leader.isLeader())
                leader.requestLeadership()
                tracker.waitUp()
                assertTrue(leader.isStarted())
                assertTrue(leader.isLeader())
                leader.relinquishLeadership()
                tracker.waitDown()
                assertTrue(leader.isStarted())
                assertFalse(leader.isLeader())
                leader.close()
                assertFalse(leader.isStarted())
                assertFalse(leader.isLeader())
                assertFalse(errors.get())
            } finally {
                artemisClient.stop()
            }
        }
    }

    @Test
    fun `Simple Leader Test Shutdown whilst leader`() {
        val artemisServer = createArtemisServer()
        artemisServer.use {
            val artemisClient = createArtemisClient()
            try {
                val counts = AtomicInteger(0)
                val errors = AtomicBoolean(false)
                val tracker = StateTracker("1", counts, errors)
                val leader = BullyLeaderClient(artemisClient, "test", "1", 10)
                leader.addLeadershipListener(tracker)
                assertFalse(leader.isStarted())
                assertFalse(leader.isLeader())
                leader.start()
                assertTrue(leader.isStarted())
                assertFalse(leader.isLeader())
                leader.requestLeadership()
                tracker.waitUp()
                assertTrue(leader.isStarted())
                assertTrue(leader.isLeader())
                leader.close()
                assertFalse(leader.isStarted())
                assertFalse(leader.isLeader())
                assertFalse(errors.get())
            } finally {
                artemisClient.stop()
            }
        }
    }

    @Test
    fun `Simple Two Leader Test`() {
        val artemisServer = createArtemisServer()
        artemisServer.use {
            val artemisClient1 = createArtemisClient()
            val artemisClient2 = createArtemisClient()
            try {
                val counts = AtomicInteger(0)
                val errors = AtomicBoolean(false)
                val tracker1 = StateTracker("1", counts, errors)
                val tracker2 = StateTracker("2", counts, errors)
                val leader1 = BullyLeaderClient(artemisClient1, "test", "1", 10)
                val leader2 = BullyLeaderClient(artemisClient2, "test", "2", 10)
                leader1.addLeadershipListener(tracker1)
                leader2.addLeadershipListener(tracker2)
                assertFalse(leader1.isStarted())
                assertFalse(leader1.isLeader())
                assertFalse(leader2.isStarted())
                assertFalse(leader2.isLeader())
                leader1.start()
                leader2.start()
                assertTrue(leader1.isStarted())
                assertFalse(leader1.isLeader())
                assertTrue(leader2.isStarted())
                assertFalse(leader2.isLeader())
                leader1.requestLeadership()
                tracker1.waitUp()
                assertTrue(leader1.isStarted())
                assertTrue(leader1.isLeader())
                assertTrue(leader2.isStarted())
                assertFalse(leader2.isLeader())
                leader1.relinquishLeadership()
                tracker1.waitDown()
                assertTrue(leader1.isStarted())
                assertFalse(leader1.isLeader())
                assertTrue(leader2.isStarted())
                assertFalse(leader2.isLeader())
                leader2.requestLeadership()
                tracker2.waitUp()
                assertTrue(leader1.isStarted())
                assertFalse(leader1.isLeader())
                assertTrue(leader2.isStarted())
                assertTrue(leader2.isLeader())
                leader1.requestLeadership()
                Thread.sleep(10000L)
                assertTrue(leader1.isLeader() xor leader2.isLeader())
                leader1.close()
                leader2.close()
                assertFalse(leader1.isStarted())
                assertFalse(leader1.isLeader())
                assertFalse(leader2.isStarted())
                assertFalse(leader2.isLeader())
                assertFalse(errors.get())
            } finally {
                artemisClient1.stop()
                artemisClient2.stop()
            }
        }
    }

    @Test
    fun `Priority Leader Test`() {
        val artemisServer = createArtemisServer()
        artemisServer.use {
            val artemisClient1 = createArtemisClient()
            val artemisClient2 = createArtemisClient()
            val artemisClient3 = createArtemisClient()
            try {
                val counts = AtomicInteger(0)
                val errors = AtomicBoolean(false)
                val tracker1 = StateTracker("1", counts, errors)
                val tracker2 = StateTracker("2", counts, errors)
                val tracker3 = StateTracker("3", counts, errors)
                val leader1 = BullyLeaderClient(artemisClient1, "test", "1", 10)
                val leader2 = BullyLeaderClient(artemisClient2, "test", "2", 20)
                val leader3 = BullyLeaderClient(artemisClient3, "test", "3", 30)
                leader1.addLeadershipListener(tracker1)
                leader2.addLeadershipListener(tracker2)
                leader3.addLeadershipListener(tracker3)
                leader1.start()
                leader2.start()
                leader3.start()
                leader3.requestLeadership() // single active leader should win
                tracker3.waitUp()
                assertFalse(leader1.isLeader())
                assertFalse(leader2.isLeader())
                assertTrue(leader3.isLeader())
                leader1.requestLeadership() // higher priority leader should preempt
                tracker1.waitUp()
                assertTrue(leader1.isLeader())
                assertFalse(leader2.isLeader())
                assertFalse(leader3.isLeader())
                leader2.requestLeadership() // lower priority leader should do nothing to existing leader, even after delay
                Thread.sleep(10000L)
                assertTrue(leader1.isLeader())
                assertFalse(leader2.isLeader())
                assertFalse(leader3.isLeader())
                leader1.relinquishLeadership() // when leader1 gives up leader2 should win as next highest active node
                tracker1.waitDown()
                tracker2.waitUp()
                assertFalse(leader1.isLeader())
                assertTrue(leader2.isLeader())
                assertFalse(leader3.isLeader())
                leader1.close()
                leader2.close()
                leader3.close()
                assertFalse(errors.get())
            } finally {
                artemisClient1.stop()
                artemisClient2.stop()
                artemisClient3.stop()
            }
        }
    }

    @Test
    fun `Multi Leader Tests`() {
        val artemisServer = createArtemisServer()
        artemisServer.use { _ ->
            val leaders = (0..9).map {
                val artemis = createArtemisClient()
                BullyLeaderClient(artemis, "test", it.toString(), 1)
            }
            val leaderCount = AtomicInteger(0)
            val failureRef = AtomicBoolean(false)
            leaders.forEach {
                val tracker = StateTracker(it.nodeId, leaderCount, failureRef)
                it.addLeadershipListener(tracker)
                it.start()
            }
            val rand = Random()
            val active = mutableSetOf<Int>()
            for (i in 0 until 30) {
                val newLeader = rand.nextInt(leaders.size)
                println("activate $newLeader")
                active.add(newLeader)
                leaders[newLeader].requestLeadership()
                val dropLeader = rand.nextInt(leaders.size)
                active.remove(dropLeader)
                println("deactivate $dropLeader $active")
                leaders[dropLeader].relinquishLeadership()
                while (active.isNotEmpty() && leaderCount.get() == 0) {
                    Thread.sleep(100)
                }
            }
            leaders.forEach {
                it.artemis.stop()
                it.close()
            }
            assertFalse(failureRef.get())
        }
    }

    @Test
    fun `Multi Leader Tests Different Priorities`() {
        val artemisServer = createArtemisServer()
        artemisServer.use { _ ->
            val leaders = (0..9).map {
                val artemis = createArtemisClient()
                BullyLeaderClient(artemis, "test", it.toString(), it)
            }
            val leaderCount = AtomicInteger(0)
            val failureRef = AtomicBoolean(false)
            leaders.forEach {
                val tracker = StateTracker(it.nodeId, leaderCount, failureRef)
                it.addLeadershipListener(tracker)
                it.start()
            }
            val rand = Random()
            val active = mutableSetOf<Int>()
            for (i in 0 until 30) {
                val newLeader = rand.nextInt(leaders.size)
                println("activate $newLeader")
                active.add(newLeader)
                leaders[newLeader].requestLeadership()
                val dropLeader = rand.nextInt(leaders.size)
                active.remove(dropLeader)
                println("deactivate $dropLeader $active")
                leaders[dropLeader].relinquishLeadership()
                while (active.isNotEmpty() && leaderCount.get() == 0) {
                    Thread.sleep(100)
                }
            }
            leaders.forEach {
                it.artemis.stop()
                it.close()
            }
            assertFalse(failureRef.get())
        }
    }

    private fun artemisReconnectionLoop(artemisClient: ArtemisSessionProvider, running: CountDownLatch) {
        artemisClient.start()
        try {
            running.await()
        } finally {
            artemisClient.stop()
        }
    }

    @Test
    fun `Disconnect Tests`() {
        val artemisConfig = createConfig(11005, true)
        val running = CountDownLatch(1)
        val artemisClient1 = createArtemisClient(artemisConfig.p2pAddress.port, started = false)
        val artemisRetryLoop1 = Thread({ artemisReconnectionLoop(artemisClient1, running) }, "Artemis Connector Thread").apply {
            isDaemon = true
        }
        artemisRetryLoop1.start()

        val artemisClient2 = createArtemisClient(artemisConfig.p2pAddress.port, started = false)
        val artemisRetryLoop2 = Thread({ artemisReconnectionLoop(artemisClient2, running) }, "Artemis Connector Thread").apply {
            isDaemon = true
        }
        artemisRetryLoop2.start()
        try {
            val leaderCount = AtomicInteger(0)
            val failureRef = AtomicBoolean(false)
            val leader1 = BullyLeaderClient(artemisClient1, "test", "1", 10)
            val leader2 = BullyLeaderClient(artemisClient2, "test", "2", 20)
            val watcher1 = StateTracker(leader1.nodeId, leaderCount, failureRef)
            leader1.addLeadershipListener(watcher1)
            val watcher2 = StateTracker(leader2.nodeId, leaderCount, failureRef)
            leader2.addLeadershipListener(watcher2)
            leader1.start()
            leader1.requestLeadership()
            leader2.start()
            leader2.requestLeadership()
            Thread.sleep(2000L)
            val server = createArtemisServer(artemisConfig.p2pAddress.port, false)
            watcher1.waitUp()
            assertTrue(leader1.isStarted())
            assertTrue(leader1.isLeader())
            assertTrue(leader2.isStarted())
            assertFalse(leader2.isLeader())
            server.stop()
            watcher1.waitDown()
            assertTrue(leader1.isStarted())
            assertFalse(leader1.isLeader())
            assertTrue(leader2.isStarted())
            assertFalse(leader2.isLeader())
            assertFalse(failureRef.get())
            leader1.relinquishLeadership()
            val server2 = createArtemisServer(artemisConfig.p2pAddress.port, false)
            watcher2.waitUp()
            assertTrue(leader1.isStarted())
            assertFalse(leader1.isLeader())
            assertTrue(leader2.isStarted())
            assertTrue(leader2.isLeader())
            server2.stop()
            leader1.close()
            leader2.close()
        } finally {
            running.countDown()
            artemisRetryLoop1.join()
            artemisRetryLoop2.join()
        }
    }

    @Test
    fun `Clients Randomly do Things`() {
        val CLIENTS_NUMBER = 10
        val ACTIONS_NUMBER = 10
        val artemisServer = createArtemisServer()
        artemisServer.use { _ ->
            val countDownLatch = CountDownLatch(CLIENTS_NUMBER)
            val leaderCount = AtomicInteger(0)
            val failureRef = AtomicBoolean(false)

            val clientList = (1..CLIENTS_NUMBER).map {
                val artemis = createArtemisClient()
                BullyLeaderClient(artemis, "test", it.toString(), 10)
            }

            clientList.forEach { client ->
                thread {
                    client.addLeadershipListener(StateTracker(client.nodeId, leaderCount, failureRef))
                    client.start()
                    val random = Random()
                    for (i in 1 until ACTIONS_NUMBER) {
                        val action = random.nextInt(2)
                        when (action) {
                            0 -> client.requestLeadership()
                            1 -> client.relinquishLeadership()
                            else -> throw IllegalArgumentException("Invalid action choice")
                        }
                        Thread.sleep(100L * random.nextInt(30))
                    }
                    client.requestLeadership() // end as possible leader
                    countDownLatch.countDown()
                }
            }

            countDownLatch.await()
            //only one leader should exist
            var timeout = 100
            while (true) {
                val leaderNumber = leaderCount.get()
                assertTrue(leaderNumber <= 1)
                if (leaderNumber == 1) {
                    break
                }
                --timeout
                assertTrue(timeout > 0)
                Thread.sleep(100L)
            }

            clientList.forEach { client ->
                client.artemis.stop()
                client.close()
            }

            assertFalse(failureRef.get())
        }
    }

    private fun createConfig(port: Int, createCerts: Boolean = false): AbstractNodeConfiguration {
        val baseDirectory = tempFolder.root.toPath()
        val certificatesDirectory = baseDirectory / "certificates"
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificatesDirectory)
        val p2pSslOptions = CertificateStoreStubs.P2P.withCertificatesDirectory(certificatesDirectory)
        val artemisConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(baseDirectory).whenever(it).baseDirectory
            doReturn(certificatesDirectory).whenever(it).certificatesDirectory
            doReturn(DUMMY_BANK_A_NAME).whenever(it).myLegalName
            doReturn(signingCertificateStore).whenever(it).signingCertificateStore
            doReturn(p2pSslOptions).whenever(it).p2pSslOptions
            doReturn(NetworkHostAndPort("localhost", port)).whenever(it).p2pAddress
            doReturn(null).whenever(it).jmxMonitoringHttpPort
            doReturn(EnterpriseConfiguration(MutualExclusionConfiguration(false, "", 20000, 40000), externalBridge = true)).whenever(it).enterpriseConfiguration
        }
        if (createCerts) {
            artemisConfig.configureWithDevSSLCertificate()
        }
        return artemisConfig
    }

    private fun createArtemisClient(port: Int = 11005, started: Boolean = true): ArtemisSessionProvider {
        val artemisConfig = createConfig(port)
        val artemisClient = ArtemisMessagingClient(artemisConfig.p2pSslOptions,
                NetworkHostAndPort("localhost", port),
                MAX_MESSAGE_SIZE,
                confirmationWindowSize = artemisConfig.enterpriseConfiguration.tuning.p2pConfirmationWindowSize,
                messagingServerConnectionConfig = MessagingServerConnectionConfiguration.CONTINUOUS_RETRY)
        if (started) {
            artemisClient.start()
        }
        return artemisClient
    }

    private fun createArtemisServer(port: Int = 11005, createCerts: Boolean = true): ArtemisMessagingServer {
        val artemisConfig = createConfig(port, createCerts)
        val artemisServer = ArtemisMessagingServer(artemisConfig, NetworkHostAndPort("0.0.0.0", port), MAX_MESSAGE_SIZE)
        artemisServer.start()
        return artemisServer
    }

}