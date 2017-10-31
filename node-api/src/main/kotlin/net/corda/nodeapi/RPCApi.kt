package net.corda.nodeapi

import net.corda.core.context.Actor
import net.corda.core.context.AuthServiceId
import net.corda.core.context.Trace
import net.corda.core.context.Trace.InvocationId
import net.corda.core.context.Trace.SessionId
import net.corda.core.identity.CordaX500Name
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.internal.SerializationPropertyKey
import net.corda.core.serialization.serialize
import net.corda.core.utilities.Id
import net.corda.core.utilities.Try
import org.apache.activemq.artemis.api.core.ActiveMQBuffer
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.client.*
import org.apache.activemq.artemis.api.core.management.CoreNotificationType
import org.apache.activemq.artemis.api.core.management.ManagementHelper
import org.apache.activemq.artemis.reader.MessageUtil
import rx.Notification
import java.time.Instant
import java.util.*

// The RPC protocol:
//
// The server consumes the queue "RPC_SERVER_QUEUE_NAME" and receives RPC requests (ClientToServer.RpcRequest) on it.
// When a client starts up it should create a queue for its inbound messages, this should be of the form
// "RPC_CLIENT_QUEUE_NAME_PREFIX.$username.$nonce". Each RPC request contains this address (in
// ClientToServer.RpcRequest.clientAddress), this is where the server will send the reply to the request as well as
// subsequent Observations rooted in the RPC. The requests/replies are muxed using a unique RpcRequestId generated by
// the client for each request.
//
// If an RPC reply's payload (ServerToClient.RpcReply.result) contains observables then the server will generate a
// unique ObservableId for each and serialise them in place of the observables themselves. Subsequently the client
// should be prepared to receive observations (ServerToClient.Observation), muxed by the relevant ObservableId.
// In addition each observation itself may contain further observables, this case should behave the same as before.
//
// Additionally the client may send ClientToServer.ObservablesClosed messages indicating that certain observables
// aren't consumed anymore, which should subsequently stop the stream from the server. Note that some observations may
// already be in flight when this is sent, the client should handle this gracefully.
//
// An example session:
// Client                              Server
//   ----------RpcRequest(RID0)----------->   // Client makes RPC request with ID "RID0"
//   <----RpcReply(RID0, Payload(OID0))----   // Server sends reply containing an observable with ID "OID0"
//   <---------Observation(OID0)-----------   // Server sends observation onto "OID0"
//   <---Observation(OID0, Payload(OID1))--   // Server sends another observation, this time containing another observable
//   <---------Observation(OID1)-----------   // Observation onto new "OID1"
//   <---------Observation(OID0)-----------
//   -----ObservablesClosed(OID0, OID1)--->   // Client indicates it stopped consuming the observables.
//   <---------Observation(OID1)-----------   // Observation was already in-flight before the previous message was processed
//                  (FIN)
//
// Note that multiple sessions like the above may interleave in an arbitrary fashion.
//
// Additionally the server may listen on client binding removals for cleanup using RPC_CLIENT_BINDING_REMOVALS. This
// requires the server to create a filter on the Artemis notification address using RPC_CLIENT_BINDING_REMOVAL_FILTER_EXPRESSION

/**
 * Constants and data types used by the RPC API.
 */
object RPCApi {

    /** Name of the Artemis queue on which the server receives RPC requests (as [ClientToServer.RpcRequest]). */
    const val RPC_SERVER_QUEUE_NAME = "rpc.server"
    /**
     * Prefix to Artemis queue names used by clients to receive communication back from a server. The full queue name
     * should be of the form "rpc.client.&lt;username&gt;.&lt;nonce&gt;".
     */
    const val RPC_CLIENT_QUEUE_NAME_PREFIX = "rpc.client"
    const val RPC_CLIENT_BINDING_REMOVALS = "rpc.clientqueueremovals"
    const val RPC_CLIENT_BINDING_ADDITIONS = "rpc.clientqueueadditions"
    const val RPC_TARGET_LEGAL_IDENTITY = "rpc-target-legal-identity"

    val RPC_CLIENT_BINDING_REMOVAL_FILTER_EXPRESSION =
            "${ManagementHelper.HDR_NOTIFICATION_TYPE} = '${CoreNotificationType.BINDING_REMOVED.name}' AND " +
                    "${ManagementHelper.HDR_ROUTING_NAME} LIKE '$RPC_CLIENT_QUEUE_NAME_PREFIX.%'"
    val RPC_CLIENT_BINDING_ADDITION_FILTER_EXPRESSION =
            "${ManagementHelper.HDR_NOTIFICATION_TYPE} = '${CoreNotificationType.BINDING_ADDED.name}' AND " +
                    "${ManagementHelper.HDR_ROUTING_NAME} LIKE '$RPC_CLIENT_QUEUE_NAME_PREFIX.%'"

    object RpcRequestOrObservableIdKey : SerializationPropertyKey<InvocationId>

    private fun ClientMessage.getBodyAsByteArray(): ByteArray {
        return ByteArray(bodySize).apply { bodyBuffer.readBytes(this) }
    }

    /**
     * Message content types which can be sent from a Corda client to a server.
     */
    sealed class ClientToServer {
        private enum class Tag {
            RPC_REQUEST,
            OBSERVABLES_CLOSED
        }

        /**
         * Request to a server to trigger the specified method with the provided arguments.
         *
         * @param clientAddress return address to contact the client at.
         * @param id a unique ID for the request, which the server will use to identify its response with.
         * @param methodName name of the method (procedure) to be called.
         * @param serialisedArguments Serialised arguments to pass to the method, if any.
         */
        data class RpcRequest(
                val clientAddress: SimpleString,
                val methodName: String,
                val serialisedArguments: ByteArray,
                val replyId: InvocationId,
                val sessionId: SessionId,
                val externalTrace: Trace? = null,
                val impersonatedActor: Actor? = null
        ) : ClientToServer() {
            fun writeToClientMessage(message: ClientMessage) {
                MessageUtil.setJMSReplyTo(message, clientAddress)
                message.putIntProperty(TAG_FIELD_NAME, Tag.RPC_REQUEST.ordinal)

                replyId.mapTo(message)
                sessionId.mapTo(message)

                externalTrace?.mapToExternal(message)
                impersonatedActor?.mapToImpersonated(message)

                message.putStringProperty(METHOD_NAME_FIELD_NAME, methodName)
                message.bodyBuffer.writeBytes(serialisedArguments)
            }
        }

        data class ObservablesClosed(val ids: List<InvocationId>) : ClientToServer() {
            fun writeToClientMessage(message: ClientMessage) {
                message.putIntProperty(TAG_FIELD_NAME, Tag.OBSERVABLES_CLOSED.ordinal)
                val buffer = message.bodyBuffer
                buffer.writeInt(ids.size)
                ids.forEach {
                    buffer.writeInvocationId(it)
                }
            }
        }

        companion object {
            fun fromClientMessage(message: ClientMessage): ClientToServer {
                val tag = Tag.values()[message.getIntProperty(TAG_FIELD_NAME)]
                return when (tag) {
                    RPCApi.ClientToServer.Tag.RPC_REQUEST -> RpcRequest(
                            clientAddress = MessageUtil.getJMSReplyTo(message),
                            methodName = message.getStringProperty(METHOD_NAME_FIELD_NAME),
                            serialisedArguments = message.getBodyAsByteArray(),
                            replyId = message.replyId(),
                            sessionId = message.sessionId(),
                            externalTrace = message.externalTrace(),
                            impersonatedActor = message.impersonatedActor()
                    )
                    RPCApi.ClientToServer.Tag.OBSERVABLES_CLOSED -> {
                        val ids = ArrayList<InvocationId>()
                        val buffer = message.bodyBuffer
                        val numberOfIds = buffer.readInt()
                        for (i in 1..numberOfIds) {
                            ids.add(buffer.readInvocationId())
                        }
                        ObservablesClosed(ids)
                    }
                }
            }
        }
    }

    /**
     * Message content types which can be sent from a Corda server back to a client.
     */
    sealed class ServerToClient {
        private enum class Tag {
            RPC_REPLY,
            OBSERVATION
        }

        abstract fun writeToClientMessage(context: SerializationContext, message: ClientMessage)

        /** Reply in response to an [ClientToServer.RpcRequest]. */
        data class RpcReply(
                val id: InvocationId,
                val result: Try<Any?>
        ) : ServerToClient() {
            override fun writeToClientMessage(context: SerializationContext, message: ClientMessage) {
                message.putIntProperty(TAG_FIELD_NAME, Tag.RPC_REPLY.ordinal)
                id.mapTo(message, RPC_ID_FIELD_NAME, RPC_ID_TIMESTAMP_FIELD_NAME)
                message.bodyBuffer.writeBytes(result.safeSerialize(context) { Try.Failure<Any>(it) }.bytes)
            }
        }

        data class Observation(
                val id: InvocationId,
                val content: Notification<*>
        ) : ServerToClient() {
            override fun writeToClientMessage(context: SerializationContext, message: ClientMessage) {
                message.putIntProperty(TAG_FIELD_NAME, Tag.OBSERVATION.ordinal)
                id.mapTo(message, OBSERVABLE_ID_FIELD_NAME, OBSERVABLE_ID_TIMESTAMP_FIELD_NAME)
                message.bodyBuffer.writeBytes(content.safeSerialize(context) { Notification.createOnError<Void?>(it) }.bytes)
            }
        }

        companion object {
            private fun Any.safeSerialize(context: SerializationContext, wrap: (Throwable) -> Any) = try {
                serialize(context = context)
            } catch (t: Throwable) {
                wrap(t).serialize(context = context)
            }

            fun fromClientMessage(context: SerializationContext, message: ClientMessage): ServerToClient {
                val tag = Tag.values()[message.getIntProperty(TAG_FIELD_NAME)]
                return when (tag) {
                    RPCApi.ServerToClient.Tag.RPC_REPLY -> {
                        val id = message.invocationId(RPC_ID_FIELD_NAME, RPC_ID_TIMESTAMP_FIELD_NAME) ?: throw IllegalStateException("Cannot parse invocation id from client message.")
                        val poolWithIdContext = context.withProperty(RpcRequestOrObservableIdKey, id)
                        RpcReply(id, message.getBodyAsByteArray().deserialize(context = poolWithIdContext))
                    }
                    RPCApi.ServerToClient.Tag.OBSERVATION -> {
                        val observableId = message.invocationId(OBSERVABLE_ID_FIELD_NAME, OBSERVABLE_ID_TIMESTAMP_FIELD_NAME) ?: throw IllegalStateException("Cannot parse invocation id from client message.")
                        val poolWithIdContext = context.withProperty(RpcRequestOrObservableIdKey, observableId)
                        val payload = message.getBodyAsByteArray().deserialize<Notification<*>>(context = poolWithIdContext)
                        Observation(observableId, payload)
                    }
                }
            }
        }
    }
}

data class ArtemisProducer(
        val sessionFactory: ClientSessionFactory,
        val session: ClientSession,
        val producer: ClientProducer
)

data class ArtemisConsumer(
        val sessionFactory: ClientSessionFactory,
        val session: ClientSession,
        val consumer: ClientConsumer
)

private val TAG_FIELD_NAME = "tag"
private val RPC_ID_FIELD_NAME = "rpc-id"
private val RPC_ID_TIMESTAMP_FIELD_NAME = "rpc-id-timestamp"
private val RPC_SESSION_ID_FIELD_NAME = "rpc-session-id"
private val RPC_SESSION_ID_TIMESTAMP_FIELD_NAME = "rpc-session-id-timestamp"
private val RPC_EXTERNAL_ID_FIELD_NAME = "rpc-external-id"
private val RPC_EXTERNAL_ID_TIMESTAMP_FIELD_NAME = "rpc-external-id-timestamp"
private val RPC_EXTERNAL_SESSION_ID_FIELD_NAME = "rpc-external-session-id"
private val RPC_EXTERNAL_SESSION_ID_TIMESTAMP_FIELD_NAME = "rpc-external-session-id-timestamp"
private val RPC_IMPERSONATED_ACTOR_ID = "rpc-impersonated-actor-id"
private val RPC_IMPERSONATED_ACTOR_STORE_ID = "rpc-impersonated-actor-store-id"
private val RPC_IMPERSONATED_ACTOR_OWNING_LEGAL_IDENTITY = "rpc-impersonated-actor-owningLegalIdentity"
private val OBSERVABLE_ID_FIELD_NAME = "observable-id"
private val OBSERVABLE_ID_TIMESTAMP_FIELD_NAME = "observable-id-timestamp"
private val METHOD_NAME_FIELD_NAME = "method-name"

fun ClientMessage.replyId(): InvocationId {

    return invocationId(RPC_ID_FIELD_NAME, RPC_ID_TIMESTAMP_FIELD_NAME) ?: throw IllegalStateException("Cannot extract reply id from client message.")
}

fun ClientMessage.sessionId(): SessionId {

    return sessionId(RPC_SESSION_ID_FIELD_NAME, RPC_SESSION_ID_TIMESTAMP_FIELD_NAME) ?: throw IllegalStateException("Cannot extract the session id from client message.")
}

fun ClientMessage.externalTrace(): Trace? {

    val invocationId = invocationId(RPC_EXTERNAL_ID_FIELD_NAME, RPC_EXTERNAL_ID_TIMESTAMP_FIELD_NAME)
    val sessionId = sessionId(RPC_EXTERNAL_SESSION_ID_FIELD_NAME, RPC_EXTERNAL_SESSION_ID_TIMESTAMP_FIELD_NAME)

    return when {
        invocationId == null || sessionId == null -> null
        else -> Trace(invocationId, sessionId)
    }
}

fun ClientMessage.impersonatedActor(): Actor? {

    return getStringProperty(RPC_IMPERSONATED_ACTOR_ID)?.let {
        val impersonatedStoreId = getStringProperty(RPC_IMPERSONATED_ACTOR_STORE_ID)
        val impersonatingOwningLegalIdentity = getStringProperty(RPC_IMPERSONATED_ACTOR_OWNING_LEGAL_IDENTITY)
        if (impersonatedStoreId == null || impersonatingOwningLegalIdentity == null) {
            throw IllegalStateException("Cannot extract impersonated actor from client message.")
        }
        Actor(Actor.Id(it), AuthServiceId(impersonatedStoreId), CordaX500Name.parse(impersonatingOwningLegalIdentity))
    }
}

private fun Id<String>.mapTo(message: ClientMessage, valueProperty: String, timestampProperty: String) {

    message.putStringProperty(valueProperty, value)
    message.putLongProperty(timestampProperty, timestamp.toEpochMilli())
}

private fun ActiveMQBuffer.writeInvocationId(invocationId: InvocationId) {

    this.writeString(invocationId.value)
    this.writeLong(invocationId.timestamp.toEpochMilli())
}

private fun ActiveMQBuffer.readInvocationId() : InvocationId {

    val value = this.readString()
    val timestamp = this.readLong()
    return InvocationId(value, Instant.ofEpochMilli(timestamp))
}

private fun InvocationId.mapTo(message: ClientMessage) = mapTo(message, RPC_ID_FIELD_NAME, RPC_ID_TIMESTAMP_FIELD_NAME)

private fun SessionId.mapTo(message: ClientMessage) = mapTo(message, RPC_SESSION_ID_FIELD_NAME, RPC_SESSION_ID_TIMESTAMP_FIELD_NAME)

private fun Trace.mapToExternal(message: ClientMessage) = mapTo(message, RPC_EXTERNAL_ID_FIELD_NAME, RPC_EXTERNAL_ID_TIMESTAMP_FIELD_NAME, RPC_EXTERNAL_SESSION_ID_FIELD_NAME, RPC_EXTERNAL_SESSION_ID_TIMESTAMP_FIELD_NAME)

private fun Actor.mapToImpersonated(message: ClientMessage) {

    message.putStringProperty(RPC_IMPERSONATED_ACTOR_ID, this.id.value)
    message.putStringProperty(RPC_IMPERSONATED_ACTOR_STORE_ID, this.serviceId.value)
    message.putStringProperty(RPC_IMPERSONATED_ACTOR_OWNING_LEGAL_IDENTITY, this.owningLegalIdentity.toString())
}

private fun Trace.mapTo(message: ClientMessage, valueProperty: String, timestampProperty: String, sessionValueProperty: String, sessionTimestampProperty: String) = apply {

    invocationId.apply {
        message.putStringProperty(valueProperty, value)
        message.putLongProperty(timestampProperty, timestamp.toEpochMilli())
    }
    sessionId.apply {
        message.putStringProperty(sessionValueProperty, value)
        message.putLongProperty(sessionTimestampProperty, timestamp.toEpochMilli())
    }
}

private fun ClientMessage.invocationId(valueProperty: String, timestampProperty: String): InvocationId? = id(valueProperty, timestampProperty, ::InvocationId)

private fun ClientMessage.sessionId(valueProperty: String, timestampProperty: String): SessionId? = id(valueProperty, timestampProperty, ::SessionId)

private fun <ID : Id<*>> ClientMessage.id(valueProperty: String, timestampProperty: String, construct: (value: String, timestamp: Instant) -> ID): ID? {

    // returning null because getLongProperty throws trying to convert null to long
    val idRaw = this.getStringProperty(valueProperty) ?: return null
    val timestampRaw = this.getLongProperty(timestampProperty)
    return construct(idRaw, Instant.ofEpochMilli(timestampRaw))
}