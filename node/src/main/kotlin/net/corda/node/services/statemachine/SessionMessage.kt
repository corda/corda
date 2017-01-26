package net.corda.node.services.statemachine

import net.corda.core.abbreviate
import net.corda.core.crypto.Party
import net.corda.core.flows.FlowException
import net.corda.core.utilities.UntrustworthyData

interface SessionMessage

interface ExistingSessionMessage : SessionMessage {
    val recipientSessionId: Long
}

data class SessionInit(val initiatorSessionId: Long, val flowName: String, val firstPayload: Any?) : SessionMessage

interface SessionInitResponse : ExistingSessionMessage

data class SessionConfirm(val initiatorSessionId: Long, val initiatedSessionId: Long) : SessionInitResponse {
    override val recipientSessionId: Long get() = initiatorSessionId
}

data class SessionReject(val initiatorSessionId: Long, val errorMessage: String) : SessionInitResponse {
    override val recipientSessionId: Long get() = initiatorSessionId
}

data class SessionData(override val recipientSessionId: Long, val payload: Any) : ExistingSessionMessage {
    override fun toString(): String {
        return "${javaClass.simpleName}(recipientSessionId=$recipientSessionId, payload=${payload.toString().abbreviate(100)})"
    }
}

data class SessionEnd(override val recipientSessionId: Long) : ExistingSessionMessage

data class ReceivedSessionMessage<out M : ExistingSessionMessage>(val sender: Party, val message: M)

fun <T> ReceivedSessionMessage<SessionData>.checkPayloadIs(type: Class<T>): UntrustworthyData<T> {
    if (type.isInstance(message.payload)) {
        return UntrustworthyData(type.cast(message.payload))
    } else {
        throw FlowException("We were expecting a ${type.name} from $sender but we instead got a " +
                "${message.payload.javaClass.name} (${message.payload})")
    }
}