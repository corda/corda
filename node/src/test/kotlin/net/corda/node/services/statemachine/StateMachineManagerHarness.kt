package net.corda.node.services.statemachine

import com.codahale.metrics.Metric
import com.nhaarman.mockito_kotlin.*
import net.corda.core.concurrent.CordaFuture
import net.corda.core.context.InvocationContext
import net.corda.core.flows.FlowInfo
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.join
import net.corda.core.serialization.SerializedBytes
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.UntrustworthyData
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.node.services.api.CheckpointStorage
import net.corda.node.services.api.MonitoringService
import net.corda.node.services.api.NetworkMapCacheInternal
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.messaging.DeduplicationHandler
import net.corda.node.services.messaging.MessagingService
import net.corda.node.services.messaging.ReceivedMessage
import net.corda.testing.internal.doLookup
import net.corda.testing.internal.participant
import net.corda.testing.internal.spectator
import org.junit.After
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.stream.Stream
import kotlin.test.assertEquals

open class StateMachineManagerHarness {
    private val onNodeReady = AtomicReference<(CordaFuture<Void?>) -> Any?>()
    private val handler = AtomicReference<(DeduplicationHandler) -> Unit>()
    private val serviceHub = participant<ServiceHubInternal>().also {
        doReturn(spectator<NodeConfiguration>().also {
            doReturn(false).whenever(it).devMode // What we really care about is production, so false.
        }).whenever(it).configuration
        doReturn(spectator<MonitoringService>().also {
            it.metrics.let {
                doAnswer { it.getArgument<Metric>(1) }.whenever(it).register(any(), any<Metric>())
            }
        }).whenever(it).monitoringService
        doReturn(participant<NetworkMapCacheInternal>().also {
            doReturn(participant<CordaFuture<Void?>>().also {
                doAnswer { onNodeReady.set(it.getArgument(0)) }.whenever(it).then<Any?>(any())
            }).whenever(it).nodeReady
        }).whenever(it).networkMapCache
        doReturn(participant<MessagingService>().also {
            doReturn(UUID.randomUUID().toString()).whenever(it).ourSenderUUID
        }).whenever(it).networkService
        doReturn(participant<SubFlowVersion>()).whenever(it).createSubFlowVersion(any())
    }
    private val checkpointStorage = spectator<CheckpointStorage>().also {
        doReturn(Stream.empty<Pair<StateMachineRunId, SerializedBytes<Checkpoint>>>()).whenever(it).getAllCheckpoints() // Start idle.
        doReturn(true).whenever(it).removeCheckpoint(any()) // Nothing cares about the return value.
    }
    private val executor = Executors.newSingleThreadExecutor()
    @After
    fun joinExecutor() = executor.join()

    private val flowMessaging = participant<FlowMessaging>().also {
        doAnswer { handler.set(it.getArgument(0)) }.whenever(it).start(any())
        doNothing().whenever(it).sendSessionMessage(any(), any(), any())
    }
    private val lookup = ConcurrentHashMap<SerializedBytes<Any>, Any>()
    private val sessionMessages = ConcurrentHashMap<ByteSequence, ExistingSessionMessage>()
    private val payloads = ConcurrentHashMap<SerializedBytes<Any>, UntrustworthyData<Any>>()
    private val serialization = participant<StateMachineSerialization>().also {
        doAnswer { invocation ->
            participant<SerializedBytes<Any>>().also {
                doReturn(1024).whenever(it).size
                lookup[it] = invocation.getArgument(0)
            }
        }.whenever(it).serialize<Any>(any(), anyOrNull())
        doLookup(sessionMessages).whenever(it).deserialize(any(), same(SessionMessage::class.java), same(null))
        doLookup(payloads).whenever(it).checkPayloadIs<Any>(any(), any())
    }
    private val nextRandomLong = AtomicLong(1)
    private val secureRandom = participant<SecureRandom>().also {
        doAnswer { nextRandomLong.andIncrement }.whenever(it).nextLong()
    }
    private val localSessions = Vector<SessionId>()
    @Rule
    @JvmField
    val verify = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                base.evaluate()
                verifyNoMoreInteractions(flowMessaging)
                assertEquals(emptyList<SessionId>(), localSessions)
            }
        }
    }
    private val smm = SingleThreadedStateMachineManager(
            serviceHub,
            checkpointStorage,
            executor,
            participant(),
            secureRandom,
            { serialization },
            spectator(),
            { localSessions.removeAt(0) },
            { flowMessaging })

    @After
    fun smmStop() = smm.stop(0)

    init {
        smm.start(participant())
        onNodeReady.get()(participant())
        verify(flowMessaging).start(handler.get())
    }

    protected fun <T> FlowLogic<T>.spawn() = smm.startFlow(this, InvocationContext.newInstance(participant()), participant(), null).getOrThrow().resultFuture
    protected inner class Channel {
        private val localSession = participant<SessionId>().also {
            doReturn(Random().nextLong()).whenever(it).toLong
            localSessions += it
        }
        private val peerSession = participant<SessionId>()
        private val peerName = participant<CordaX500Name>()
        val peer = participant<Party>()

        init {
            serviceHub.networkMapCache.let {
                doReturn(peer).whenever(it).getPeerByLegalName(peerName)
            }
        }

        private fun expect(total: Int, predicate: (SessionMessage) -> Boolean) {
            // When running tests in a loop this timeout may be achieved, which I suspect is due to something filling up the heap:
            verify(flowMessaging, timeout(5000).times(total)).sendSessionMessage(same(peer), argWhere(predicate), any())
        }

        fun expectData(total: Int = 1, predicate: (Any) -> Boolean) = expect(total) {
            it is ExistingSessionMessage && it.recipientSessionId == peerSession && it.payload.let {
                it is DataSessionMessage && predicate(lookup[it.payload]!!)
            }
        }

        private fun message(payload: ExistingSessionMessagePayload) = handler.get()(spectator<DeduplicationHandler>().also { dh ->
            doReturn(participant<ExternalEvent.ExternalMessageEvent>().also {
                doReturn(participant<ReceivedMessage>().also {
                    doReturn(peerName).whenever(it).peer
                    doReturn(participant<ByteSequence>().also {
                        sessionMessages[it] = participant<ExistingSessionMessage>().also {
                            doReturn(localSession).whenever(it).recipientSessionId
                            doReturn(payload).whenever(it).payload
                        }
                    }).whenever(it).data
                }).whenever(it).receivedMessage
                doReturn(dh).whenever(it).deduplicationHandler
            }).whenever(dh).externalCause
        })

        fun handshake(predicate: (Any?) -> Boolean) {
            expect(1) {
                it is InitialSessionMessage && it.initiatorSessionId == localSession && predicate(it.firstPayload?.let { lookup[it]!! })
            }
            message(participant<ConfirmSessionMessage>().also {
                doReturn(participant<FlowInfo>()).whenever(it).initiatedFlowInfo
                doReturn(peerSession).whenever(it).initiatedSessionId
            })
        }

        fun dataMessage(data: Any) = message(participant<DataSessionMessage>().also {
            doReturn(participant<SerializedBytes<Any>>().also {
                payloads[it] = participant<UntrustworthyData<Any>>().also {
                    doReturn(data).whenever(it).unwrap { it }
                }
            }).whenever(it).payload
        })

        fun <T> run(task: Channel.() -> T) = task().also {
            expect(1) { it is ExistingSessionMessage && it.recipientSessionId == peerSession && it.payload == EndSessionMessage }
        }
    }
}
