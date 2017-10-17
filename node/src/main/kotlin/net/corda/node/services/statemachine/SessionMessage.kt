package net.corda.node.services.statemachine

import net.corda.core.flows.FlowException
import net.corda.core.flows.UnexpectedFlowEndException
import net.corda.core.identity.Party
import net.corda.core.internal.castIfPossible
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.SerializedBytes
import net.corda.core.utilities.UntrustworthyData
import java.io.IOException

@CordaSerializable
interface SessionMessage

interface ExistingSessionMessage : SessionMessage {
    val recipientSessionId: Long
}

interface SessionInitResponse : ExistingSessionMessage {
    val initiatorSessionId: Long
    override val recipientSessionId: Long get() = initiatorSessionId
}

interface SessionEnd : ExistingSessionMessage

data class SessionInit(val initiatorSessionId: Long,
                       val initiatingFlowClass: String,
                       val flowVersion: Int,
                       val appName: String,
                       val firstPayload: SerializedBytes<Any>?) : SessionMessage

data class SessionConfirm(override val initiatorSessionId: Long,
                          val initiatedSessionId: Long,
                          val flowVersion: Int,
                          val appName: String) : SessionInitResponse

data class SessionReject(override val initiatorSessionId: Long, val errorMessage: String) : SessionInitResponse

data class SessionData(override val recipientSessionId: Long, val payload: SerializedBytes<Any>) : ExistingSessionMessage

data class NormalSessionEnd(override val recipientSessionId: Long) : SessionEnd

data class ErrorSessionEnd(override val recipientSessionId: Long, val errorResponse: FlowException?) : SessionEnd

data class ReceivedSessionMessage<out M : ExistingSessionMessage>(val sender: Party, val message: M)

fun <T : Any> ReceivedSessionMessage<SessionData>.checkPayloadIs(type: Class<T>): UntrustworthyData<T> {
    val payloadData: T = try {
        val serializer = SerializationDefaults.SERIALIZATION_FACTORY
        serializer.deserialize<T>(message.payload, type, SerializationDefaults.P2P_CONTEXT)
    } catch (ex: Exception) {
        throw IOException("Payload invalid", ex)
    }
    return type.castIfPossible(payloadData)?.let { UntrustworthyData(it) } ?:
            throw UnexpectedFlowEndException("We were expecting a ${type.name} from $sender but we instead got a " +
                    "${payloadData.javaClass.name} (${payloadData})")

}
