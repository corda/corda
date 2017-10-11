package net.corda.nodeapi

import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.sequence
import net.corda.nodeapi.internal.serialization.AMQP_P2P_CONTEXT
import net.corda.nodeapi.internal.serialization.KRYO_P2P_CONTEXT
import net.corda.nodeapi.internal.serialization.KryoHeaderV0_1
import net.corda.nodeapi.internal.serialization.amqp.AmqpHeaderV1_0
import net.corda.nodeapi.internal.serialization.obtainHeaderSignature
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.client.ClientMessage
import org.apache.activemq.artemis.reader.MessageUtil

object VerifierApi {
    const val VERIFIER_USERNAME = "SystemUsers/Verifier"
    const val VERIFICATION_REQUESTS_QUEUE_NAME = "verifier.requests"
    const val VERIFICATION_RESPONSES_QUEUE_NAME_PREFIX = "verifier.responses"
    private const val VERIFICATION_ID_FIELD_NAME = "id"
    private const val RESULT_EXCEPTION_FIELD_NAME = "result-exception"

    data class VerificationRequest(
            val verificationId: Long,
            val transaction: LedgerTransaction,
            val responseAddress: SimpleString
    ) {
        companion object {
            fun fromClientMessage(message: ClientMessage): VerificationRequest {
                val bytes = ByteArray(message.bodySize).apply { message.bodyBuffer.readBytes(this) }
                val bytesSequence = bytes.sequence()
                val context = establishSuitableContext(bytesSequence.obtainHeaderSignature())
                return VerificationRequest(
                        message.getLongProperty(VERIFICATION_ID_FIELD_NAME),
                        bytesSequence.deserialize(context = context),
                        MessageUtil.getJMSReplyTo(message)
                )
            }

            private fun establishSuitableContext(headerSignature: ByteSequence): SerializationContext =
                    when (headerSignature) {
                        KryoHeaderV0_1 -> KRYO_P2P_CONTEXT
                        AmqpHeaderV1_0 -> AMQP_P2P_CONTEXT
                        else -> throw IllegalArgumentException("Unrecognised header signature '$headerSignature'")
                    }
        }

        fun writeToClientMessage(message: ClientMessage) {
            message.putLongProperty(VERIFICATION_ID_FIELD_NAME, verificationId)
            message.writeBodyBufferBytes(transaction.serialize().bytes)
            MessageUtil.setJMSReplyTo(message, responseAddress)
        }
    }

    data class VerificationResponse(
            val verificationId: Long,
            val exception: Throwable?
    ) {
        companion object {
            fun fromClientMessage(message: ClientMessage): VerificationResponse {
                return VerificationResponse(
                        message.getLongProperty(VERIFICATION_ID_FIELD_NAME),
                        message.getBytesProperty(RESULT_EXCEPTION_FIELD_NAME)?.deserialize()
                )
            }
        }

        fun writeToClientMessage(message: ClientMessage) {
            message.putLongProperty(VERIFICATION_ID_FIELD_NAME, verificationId)
            if (exception != null) {
                message.putBytesProperty(RESULT_EXCEPTION_FIELD_NAME, exception.serialize().bytes)
            }
        }
    }
}
