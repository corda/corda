package net.corda.nodeapi.internal.bully

import net.corda.core.internal.ThreadBox
import net.corda.core.internal.VisibleForTesting
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.debug
import net.corda.nodeapi.internal.ArtemisMessagingComponent
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.INTERNAL_PREFIX
import net.corda.nodeapi.internal.ArtemisSessionProvider
import net.corda.nodeapi.internal.zookeeper.CordaLeaderListener
import net.corda.nodeapi.internal.zookeeper.ZkLeader
import org.apache.activemq.artemis.api.core.ActiveMQObjectClosedException
import org.apache.activemq.artemis.api.core.RoutingType
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.client.*
import java.time.Clock
import java.time.Instant
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class BullyLeaderClient(val artemis: ArtemisSessionProvider,
                        private val electionPath: String,
                        val nodeId: String,
                        val priority: Int) : ZkLeader {

    private companion object {
        const val LEADER_TIMEOUT_MSEC = 1000L
        private val log = contextLogger()
    }

    @VisibleForTesting
    var clock: Clock = Clock.systemUTC()


    @CordaSerializable
    enum class MessageType {
        ELECTION_REQUEST,
        ELECTION_REJECT,
        LEADER_ANNOUNCE,
        LEADER_RETIRE
    }

    @CordaSerializable
    data class LeaderMessage(val msgType: MessageType,
                                     val term: Int,
                                     val proposedLeader: String,
                                     val leaderPriority: Int)

    private class ArtemisMessageSession private constructor(
            private val leaderTopic: SimpleString,
            private val session: ClientSession,
            private val messageConsumer: ClientConsumer,
            private val messageProducer: ClientProducer,
            private val onMessage: (LeaderMessage) -> Unit) : AutoCloseable, FailoverEventListener {
        companion object {
            fun connectToArtemis(electionPath: String, locator: ServerLocator, factory: ClientSessionFactory, onMessage: (LeaderMessage) -> Unit): ArtemisMessageSession {
                val session = factory.createSession(ArtemisMessagingComponent.NODE_P2P_USER,
                        ArtemisMessagingComponent.NODE_P2P_USER,
                        false,
                        true,
                        true,
                        locator.isPreAcknowledge,
                        ActiveMQClient.DEFAULT_ACK_BATCH_SIZE)
                val producer = session.createProducer()
                val leaderTopic = SimpleString("${INTERNAL_PREFIX}leader.$electionPath")
                val queueName = SimpleString(UUID.randomUUID().toString())
                session.createTemporaryQueue(leaderTopic, RoutingType.MULTICAST, queueName)
                val consumer = session.createConsumer(queueName)
                val artemisMessageSession = ArtemisMessageSession(leaderTopic, session, consumer, producer, onMessage)
                session.addFailoverListener(artemisMessageSession)
                consumer.setMessageHandler { msg ->
                    artemisMessageSession.processMessage(msg)
                }
                session.start()
                return artemisMessageSession
            }
        }

        private val closed: AtomicBoolean = AtomicBoolean(false)
        private val _connected: AtomicBoolean = AtomicBoolean(true)
        val connected: Boolean get() = _connected.get()

        override fun failoverEvent(eventType: FailoverEventType) {
            log.warn("Artemis Failover event $eventType")
            _connected.set(eventType == FailoverEventType.FAILOVER_COMPLETED)
        }

        private fun processMessage(message: ClientMessage) {
            try {
                val data: ByteArray = ByteArray(message.bodySize).apply { message.bodyBuffer.readBytes(this) }
                val leaderMessage = data.deserialize<LeaderMessage>(context = SerializationDefaults.P2P_CONTEXT)
                onMessage(leaderMessage)
                message.acknowledge()
            } catch (ex: Exception) {
                log.error("Unable to process leader control message", ex)
            }
        }

        fun sendMessage(message: LeaderMessage) {
            if (closed.get() || session.isClosed || !connected) return
            val messageBytes = message.serialize(context = SerializationDefaults.P2P_CONTEXT).bytes
            val artemisMessage = session.createMessage(false)
            artemisMessage.writeBodyBufferBytes(messageBytes)
            messageProducer.send(leaderTopic, artemisMessage)
        }

        override fun close() {
            if (!closed.getAndSet(true)) {
                thread {
                    // send of to another thread as this can block for ages inside Artemis
                    closeInternal()
                }
            }
        }

        private fun close(target: AutoCloseable) {
            try {
                target.close()
            } catch (ignored: ActiveMQObjectClosedException) {
                // swallow
            }
        }

        private fun closeInternal() {
            session.removeFailoverListener(this)
            if (!messageConsumer.isClosed) {
                close(messageConsumer)
            }
            if (!session.isClosed) {
                close(session)
            }
        }
    }

    private enum class BullyState {
        FOLLOWER,
        INITIATE_ELECTION,
        POSSIBLE_LEADER,
        LEADER
    }

    private class LeaderState(clock: Clock) {
        var scheduler: ScheduledExecutorService? = null
        val isStarted: Boolean get() = (scheduler?.isShutdown?.not() ?: false)
        var pollTimer: ScheduledFuture<*>? = null
        var isActive: Boolean = false
        var messageSession: ArtemisMessageSession? = null
        var state: BullyState = BullyState.FOLLOWER
        var currentTerm: Int = 0
        var currentLeader: String? = null
        var leaderPriority: Int? = null
        var leaderUpdated: Instant = clock.instant()
    }

    private val state = ThreadBox(LeaderState(clock))
    private val listeners = CopyOnWriteArrayList<CordaLeaderListener>()

    override fun start() {
        state.locked {
            if (isStarted) return
            val newScheduler = Executors.newSingleThreadScheduledExecutor()
            scheduler = newScheduler
            pollTimer = newScheduler.scheduleAtFixedRate(::timerEvent, 0L, LEADER_TIMEOUT_MSEC / 2L, TimeUnit.MILLISECONDS)
        }
    }

    override fun close() {
        if (isStarted()) {
            relinquishLeadership()
        }
        state.locked {
            messageSession?.close()
            messageSession = null
            pollTimer?.cancel(false)
            pollTimer = null
            scheduler?.shutdown()
            scheduler = null
        }
    }

    private fun timerEvent() {
        var down = false
        state.locked {
            if (!isStarted) return
            log.debug { "$nodeId Timer running. current state $state" }
            val wasLeader = isLeader()
            val artemis = artemis.started
            if (artemis == null || artemis.session.isClosed) {
                log.info("Artemis lost")
                messageSession?.close()
                messageSession = null
                state = BullyState.FOLLOWER
                currentLeader = null
                leaderPriority = null
                if (wasLeader) down = true
                return@locked
            }
            if (messageSession == null) {
                log.info("$nodeId Artemis connected. Creating leader session")
                messageSession = ArtemisMessageSession.connectToArtemis(electionPath,
                        artemis.serverLocator,
                        artemis.sessionFactory,
                        ::messageEvent)
                return@locked
            }
            if (!messageSession!!.connected) {
                state = BullyState.FOLLOWER
                currentLeader = null
                leaderPriority = null
                if (wasLeader) down = true
                return@locked
            }
            if (!isActive) {
                log.debug { "$nodeId Idling as inactive" }
                state = BullyState.FOLLOWER
                if (wasLeader) down = true
                return@locked
            }
            val now = clock.instant()
            when (state) {
                BullyState.FOLLOWER -> {
                    // Unknown Leader, or dead leader prepare to do election
                    if (currentLeader == null || leaderUpdated.plusMillis(LEADER_TIMEOUT_MSEC).isBefore(now)) {
                        log.info("$nodeId Leader missing start election")
                        currentLeader = null
                        leaderPriority = null
                        state = BullyState.INITIATE_ELECTION
                    } else if (compareIds(nodeId, priority, currentLeader!!, leaderPriority!!) > 0) { // we are higher priority start elections
                        log.info("$nodeId Leader lower priority than us start challenge")
                        currentLeader = null
                        leaderPriority = null
                        state = BullyState.INITIATE_ELECTION
                    }
                }
                BullyState.INITIATE_ELECTION -> {
                    leaderUpdated = now // move time to keep track of election announcements
                    state = BullyState.POSSIBLE_LEADER
                    val message = LeaderMessage(MessageType.ELECTION_REQUEST,
                            currentTerm,
                            nodeId,
                            priority)
                    log.debug { "$nodeId Send election message $message" }
                    messageSession!!.sendMessage(message)
                }
                BullyState.POSSIBLE_LEADER -> {
                    val message = LeaderMessage(MessageType.ELECTION_REQUEST,
                            currentTerm,
                            nodeId,
                            priority)
                    log.debug { "$nodeId Send election message $message" }
                    messageSession!!.sendMessage(message)
                    if (leaderUpdated.plusMillis(2 * LEADER_TIMEOUT_MSEC).isBefore(now)) { // everyone had a fair chance
                        log.info("$nodeId transition to leader state")
                        state = BullyState.LEADER
                        ++currentTerm
                    }
                }
                BullyState.LEADER -> {
                    val message = LeaderMessage(MessageType.LEADER_ANNOUNCE,
                            currentTerm,
                            nodeId,
                            priority)
                    log.debug { "$nodeId Broadcast leader heartbeat $message" }
                    messageSession!!.sendMessage(message)
                }
            }
        }
        if (down) {
            lostLeadership()
        }
    }

    private fun compareIds(nodeId1: String, priority1: Int, nodeId2: String, priority2: Int): Int {
        if (priority1 > priority2) { // smaller priority value wins
            return -1
        } else if (priority1 < priority2) {
            return +1
        }
        if (nodeId1 < nodeId2) {
            return -1
        } else if (nodeId2 > nodeId1) {
            return +1
        }
        return 0
    }

    private fun messageEvent(message: LeaderMessage) {
        log.debug { "$nodeId received message $message" }
        var up = false
        var down = false
        state.locked {
            if (!isStarted) return
            if (message.term < currentTerm) { // ignore old messages
                log.debug { "$nodeId discard outdated message" }
                return
            }
            val now = clock.instant()
            val wasLeader = isLeader()
            if (message.term > currentTerm) { // we are out of sync, reset and consider our position
                log.debug { "$nodeId new term detected resetting" }
                currentTerm = message.term
                state = BullyState.FOLLOWER
                leaderUpdated = now
                currentLeader = message.proposedLeader
                leaderPriority = message.leaderPriority
                if (wasLeader) down = true
                return@locked
            }
            val comparison = compareIds(nodeId, priority, message.proposedLeader, message.leaderPriority)
            if (comparison < 0) { // we are lower priority so ensure we are just a follower
                if (state != BullyState.FOLLOWER) {
                    state = BullyState.FOLLOWER
                    log.info("$nodeId terminate our involvement in election possible leader ${message.proposedLeader}")
                }
                leaderUpdated = now
                currentLeader = message.proposedLeader
                leaderPriority = message.leaderPriority
            }
            when (message.msgType) {
                MessageType.ELECTION_REQUEST -> {
                    if (isActive && (comparison > 0)) { // we are higher priority, reject them
                        log.debug { "$nodeId send rebuttal to lower priority node" }
                        messageSession!!.sendMessage(LeaderMessage(MessageType.ELECTION_REJECT,
                                currentTerm,
                                currentLeader ?: nodeId,
                                leaderPriority ?: priority))
                    }
                }
                MessageType.ELECTION_REJECT -> {
                    // Nothing to do here, handled by priority check above
                }
                MessageType.LEADER_ANNOUNCE -> {
                    leaderUpdated = now
                    currentLeader = message.proposedLeader
                    leaderPriority = message.leaderPriority
                    if ((state == BullyState.LEADER) && (currentLeader != nodeId)) { // shouldn't happen, but reset now!!
                        log.error("$nodeId unexpected leader announcement")
                        state = BullyState.FOLLOWER
                    }
                }
                MessageType.LEADER_RETIRE -> { // polite leader shutdown
                    if (isActive) {
                        log.info("$nodeId retirement announced. Starting election")
                        state = BullyState.INITIATE_ELECTION
                        leaderUpdated = now
                        currentLeader = null
                        leaderPriority = null
                    }
                }
            }
            val isLeader = isLeader()
            if (wasLeader xor isLeader) {
                up = isLeader
                down = !isLeader
            }
        }
        if (down) {
            lostLeadership()
        }
        if (up) {
            acquireLeadership()
        }
    }

    override fun requestLeadership() {
        state.locked {
            require(isStarted) { "Leader elector must be started first" }
            isActive = true
        }
    }

    override fun relinquishLeadership() {
        val wasLeader = state.locked {
            require(isStarted) { "Leader elector must be started first" }
            val oldState = isLeader()
            currentLeader = null
            leaderPriority = null
            state = BullyState.FOLLOWER
            isActive = false
            oldState
        }
        if (wasLeader) {
            lostLeadership()
            state.locked {
                log.debug { "$nodeId tell other nodes to start election" }
                messageSession?.sendMessage(LeaderMessage(MessageType.LEADER_RETIRE,
                        currentTerm,
                        nodeId,
                        priority))
            }
        }
    }

    private fun acquireLeadership() {
        for (listener in listeners) {
            listener.isLeader()
        }
    }

    private fun lostLeadership() {
        for (listener in listeners) {
            listener.notLeader()
        }
    }

    override fun addLeadershipListener(listener: CordaLeaderListener) {
        listeners += listener
    }


    override fun removeLeadershipListener(listener: CordaLeaderListener) {
        listeners -= listener
    }

    override fun isLeader(): Boolean = state.locked { isStarted && isActive && (state == BullyState.LEADER) && (currentLeader == nodeId) }

    override fun isStarted(): Boolean = state.locked { isStarted }

}