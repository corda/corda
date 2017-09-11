package net.corda.node.services.statemachine

import net.corda.core.flows.FlowException
import net.corda.core.flows.UnexpectedFlowEndException
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.castIfPossible
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.UntrustworthyData

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
                       val firstPayload: Any?,
                       // Left as a placeholder for support of multiple identities on a node. For now we choose the first one as a special one.
                       val otherIdentity: PartyAndCertificate? = null) : SessionMessage

data class SessionConfirm(override val initiatorSessionId: Long,
                          val initiatedSessionId: Long,
                          val flowVersion: Int,
                          val appName: String) : SessionInitResponse

data class SessionReject(override val initiatorSessionId: Long, val errorMessage: String) : SessionInitResponse

data class SessionData(override val recipientSessionId: Long, val payload: Any) : ExistingSessionMessage

data class NormalSessionEnd(override val recipientSessionId: Long) : SessionEnd

data class ErrorSessionEnd(override val recipientSessionId: Long, val errorResponse: FlowException?) : SessionEnd

data class ReceivedSessionMessage<out M : ExistingSessionMessage>(val sender: Party, val message: M)

fun <T> ReceivedSessionMessage<SessionData>.checkPayloadIs(type: Class<T>): UntrustworthyData<T> {
    return type.castIfPossible(message.payload)?.let { UntrustworthyData(it) } ?:
            throw UnexpectedFlowEndException("We were expecting a ${type.name} from $sender but we instead got a " +
                    "${message.payload.javaClass.name} (${message.payload})")
}
