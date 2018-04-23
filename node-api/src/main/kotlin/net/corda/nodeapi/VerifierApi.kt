/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi

import net.corda.core.serialization.*
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.sequence
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
            fun fromClientMessage(message: ClientMessage): ObjectWithCompatibleContext<VerificationRequest> {
                val bytes = ByteArray(message.bodySize).apply { message.bodyBuffer.readBytes(this) }
                val bytesSequence = bytes.sequence()
                val (transaction, context) = bytesSequence.deserializeWithCompatibleContext<LedgerTransaction>()
                val request = VerificationRequest(
                        message.getLongProperty(VERIFICATION_ID_FIELD_NAME),
                        transaction,
                        MessageUtil.getJMSReplyTo(message))
                return ObjectWithCompatibleContext(request, context)
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

        fun writeToClientMessage(message: ClientMessage, context: SerializationContext) {
            message.putLongProperty(VERIFICATION_ID_FIELD_NAME, verificationId)
            if (exception != null) {
                message.putBytesProperty(RESULT_EXCEPTION_FIELD_NAME, exception.serialize(context = context).bytes)
            }
        }
    }
}