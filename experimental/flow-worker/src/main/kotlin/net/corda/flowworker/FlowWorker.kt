package net.corda.flowworker

import net.corda.core.concurrent.CordaFuture
import net.corda.core.context.InvocationContext
import net.corda.core.context.Trace
import net.corda.core.crypto.toStringShort
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.FlowStateMachine
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.node.services.messaging.DeduplicationHandler
import net.corda.node.services.messaging.P2PMessagingClient
import net.corda.node.services.statemachine.ExternalEvent
import net.corda.nodeapi.internal.ArtemisMessagingClient
import net.corda.nodeapi.internal.ArtemisMessagingComponent
import org.apache.activemq.artemis.api.core.RoutingType
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.client.ClientMessage
import org.apache.activemq.artemis.api.core.client.ClientProducer
import org.apache.activemq.artemis.api.core.client.ClientSession
import java.util.*
import kotlin.concurrent.thread

class FlowWorker(flowWorkerId: String, private val flowWorkerServiceHub: FlowWorkerServiceHub) {

    companion object {
        const val FLOW_WORKER_QUEUE_ADDRESS_PREFIX = "${ArtemisMessagingComponent.INTERNAL_PREFIX}flow.worker."
    }

    private val queueAddress = "$FLOW_WORKER_QUEUE_ADDRESS_PREFIX${flowWorkerServiceHub.myInfo.legalIdentities[0].owningKey.toStringShort()}"
    private val queueName = "$queueAddress.$flowWorkerId"

    private val runOnStop = ArrayList<() -> Any?>()

    fun start() {
        flowWorkerServiceHub.start()
        runOnStop += { flowWorkerServiceHub.stop() }

        val flowWorkerMessagingClient = ArtemisMessagingClient(flowWorkerServiceHub.configuration, flowWorkerServiceHub.configuration.messagingServerAddress!!, flowWorkerServiceHub.networkParameters.maxMessageSize)
        runOnStop += { flowWorkerMessagingClient.stop() }

        val session = flowWorkerMessagingClient.start().session

        val queueQuery = session.queueQuery(SimpleString(queueName))
        if (!queueQuery.isExists) {
            session.createQueue(queueAddress, RoutingType.ANYCAST, queueName, true)
        }

        val consumer = session.createConsumer(queueName)
        val producer = session.createProducer()

        consumer.setMessageHandler { message -> handleFlowWorkerMessage(message, session, producer) }

        thread {
            (flowWorkerServiceHub.networkService as P2PMessagingClient).run()
        }
    }

    private fun handleFlowWorkerMessage(message: ClientMessage, session: ClientSession, producer: ClientProducer) {
        val data = ByteArray(message.bodySize).apply { message.bodyBuffer.readBytes(this) }
        val flowWorkerMessage = data.deserialize<FlowWorkerMessage>(context = SerializationDefaults.RPC_SERVER_CONTEXT)

        when (flowWorkerMessage) {
            is StartFlow -> handleStartFlowMessage(flowWorkerMessage, session, producer)
            is NetworkMapUpdate -> handleNetworkMapUpdateMessage(flowWorkerMessage)
        }
    }

    private fun handleStartFlowMessage(startFlowMessage: StartFlow, session: ClientSession, producer: ClientProducer) {
        val logicRef = flowWorkerServiceHub.flowLogicRefFactory.createForRPC(startFlowMessage.logicType, *startFlowMessage.args)
        val logic: FlowLogic<*> = uncheckedCast(flowWorkerServiceHub.flowLogicRefFactory.toFlowLogic(logicRef))
        val result = startFlow(logic, startFlowMessage.context).get()

        val stateMachineRunIdMessage = session.createMessage(true)
        stateMachineRunIdMessage.writeBodyBufferBytes(FlowReplyStateMachineRunId(flowWorkerServiceHub.myInfo.legalIdentities.first().name, startFlowMessage.replyId, result.id).serialize(context = SerializationDefaults.RPC_SERVER_CONTEXT).bytes)
        producer.send(startFlowMessage.clientAddress, stateMachineRunIdMessage)

        result.resultFuture.then {
            val resultMessage = session.createMessage(true)
            resultMessage.writeBodyBufferBytes(FlowReplyResult(flowWorkerServiceHub.myInfo.legalIdentities.first().name, startFlowMessage.replyId, it.get()).serialize(context = SerializationDefaults.RPC_SERVER_CONTEXT).bytes)
            producer.send(startFlowMessage.clientAddress, resultMessage)
        }
    }

    private fun handleNetworkMapUpdateMessage(networkMapUpdateMessage: NetworkMapUpdate) {
        val mapChange = networkMapUpdateMessage.mapChange
        // TODO remove
        if (mapChange is NetworkMapCache.MapChange.Added) {
            flowWorkerServiceHub.networkMapCache.addNode(mapChange.node)
            mapChange.node.legalIdentitiesAndCerts.forEach {
                try {
                    flowWorkerServiceHub.identityService.verifyAndRegisterIdentity(it)
                } catch (ignore: Exception) {
                    // Log a warning to indicate node info is not added to the network map cache.
                    // NetworkMapCacheImpl.logger.warn("Node info for :'${it.name}' is not added to the network map due to verification error.")
                }
            }
        }
    }

    fun stop() {
        for (toRun in runOnStop.reversed()) {
            toRun()
        }
        runOnStop.clear()
    }

    private fun <T> startFlow(logic: FlowLogic<T>, context: InvocationContext): CordaFuture<FlowStateMachine<T>> {
        val startFlowEvent = object : ExternalEvent.ExternalStartFlowEvent<T>, DeduplicationHandler {
            override fun insideDatabaseTransaction() {}

            override fun afterDatabaseTransaction() {}

            override val externalCause: ExternalEvent
                get() = this
            override val deduplicationHandler: DeduplicationHandler
                get() = this

            override val flowLogic: FlowLogic<T>
                get() = logic
            override val context: InvocationContext
                get() = context

            override fun wireUpFuture(flowFuture: CordaFuture<FlowStateMachine<T>>) {
                _future.captureLater(flowFuture)
            }

            private val _future = openFuture<FlowStateMachine<T>>()
            override val future: CordaFuture<FlowStateMachine<T>>
                get() = _future

        }
        flowWorkerServiceHub.database.transaction {
            flowWorkerServiceHub.smm.deliverExternalEvent(startFlowEvent)
        }
        return startFlowEvent.future
    }
}

@CordaSerializable
sealed class FlowWorkerMessage() {
    abstract val legalName: CordaX500Name
}

data class StartFlow(override val legalName: CordaX500Name, val logicType: Class<out FlowLogic<*>>, val args: Array<out Any?>, val context: InvocationContext, val clientAddress: String, val replyId: Trace.InvocationId) : FlowWorkerMessage()

data class FlowReplyStateMachineRunId(override val legalName: CordaX500Name, val replyId: Trace.InvocationId, val id: StateMachineRunId) : FlowWorkerMessage()

data class FlowReplyResult(override val legalName: CordaX500Name, val replyId: Trace.InvocationId, val result: Any?) : FlowWorkerMessage()

data class NetworkMapUpdate(override val legalName: CordaX500Name, val mapChange: NetworkMapCache.MapChange) : FlowWorkerMessage()