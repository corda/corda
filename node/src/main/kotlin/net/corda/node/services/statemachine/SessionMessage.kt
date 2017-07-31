package net.corda.node.services.statemachine

import net.corda.core.flows.FlowException
import net.corda.core.flows.UnexpectedFlowEndException
import net.corda.core.identity.Party
import net.corda.core.internal.castIfPossible
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.UntrustworthyData

/**
 * These internal messages define the flow session protocol.
 */

@CordaSerializable
interface SessionMessage

data class SessionInit(val initiatorSessionId: Long,
                       val initiatingFlowClass: String,
                       val flowVerison: Int,
                       val firstPayload: Any?) : SessionMessage

interface ExistingSessionMessage : SessionMessage {
    val recipientSessionId: Long
}

data class SessionData(override val recipientSessionId: Long, val payload: Any) : ExistingSessionMessage {
    override fun toString(): String = "${javaClass.simpleName}(recipientSessionId=$recipientSessionId, payload=$payload)"
}

interface SessionInitResponse : ExistingSessionMessage {
    val initiatorSessionId: Long
    override val recipientSessionId: Long get() = initiatorSessionId
}

data class SessionConfirm(override val initiatorSessionId: Long, val initiatedSessionId: Long) : SessionInitResponse
data class SessionReject(override val initiatorSessionId: Long, val errorMessage: String) : SessionInitResponse

interface SessionEnd : ExistingSessionMessage
data class NormalSessionEnd(override val recipientSessionId: Long) : SessionEnd
data class ErrorSessionEnd(override val recipientSessionId: Long, val errorResponse: FlowException?) : SessionEnd

data class ReceivedSessionMessage<out M : ExistingSessionMessage>(val sender: Party, val message: M)

fun <T> ReceivedSessionMessage<SessionData>.checkPayloadIs(type: Class<T>): UntrustworthyData<T> {
    return type.castIfPossible(message.payload)?.let { UntrustworthyData(it) } ?:
            throw UnexpectedFlowEndException("We were expecting a ${type.name} from $sender but we instead got a " +
                    "${message.payload.javaClass.name} (${message.payload})")
}
