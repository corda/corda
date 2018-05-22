/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.exceptions

import net.corda.core.CordaRuntimeException
import net.corda.core.crypto.SecureHash
import net.corda.core.ClientRelevantError
import net.corda.core.flows.IdentifiableException
import net.corda.core.serialization.CordaSerializable

/**
 * Thrown to indicate that an attachment was already uploaded to a Corda node.
 */
class DuplicateAttachmentException(attachmentHash: String) : java.nio.file.FileAlreadyExistsException(attachmentHash), ClientRelevantError

/**
 * Thrown to indicate that a flow was not designed for RPC and should be started from an RPC client.
 */
class NonRpcFlowException(logicType: Class<*>) : IllegalArgumentException("${logicType.name} was not designed for RPC"), ClientRelevantError

/**
 * An [Exception] to signal RPC clients that something went wrong within a Corda node.
 * The message is generic on purpose, as this prevents internal information from reaching RPC clients.
 * Leaking internal information outside can compromise privacy e.g., party names and security e.g., passwords, stacktraces, etc.
 *
 * @param errorIdentifier an optional identifier for tracing problems across parties.
 */
class InternalNodeException(private val errorIdentifier: Long? = null) : CordaRuntimeException(message), ClientRelevantError, IdentifiableException {

    companion object {
        /**
         * Message for the exception.
         */
        const val message = "Something went wrong within the Corda node."
    }

    override fun getErrorId(): Long? {
        return errorIdentifier
    }
}

class OutdatedNetworkParameterHashException(old: SecureHash, new: SecureHash) : CordaRuntimeException(TEMPLATE.format(old, new)), ClientRelevantError {

    private companion object {
        private const val TEMPLATE = "Refused to accept parameters with hash %s because network map advertises update with hash %s. Please check newest version"
    }
}

/**
 * Thrown to indicate that the command was rejected by the node, typically due to a special temporary mode.
 */
class RejectedCommandException(message: String) : CordaRuntimeException(message), ClientRelevantError

/**
 * Allows an implementing [Throwable] to be propagated to RPC clients.
 */
@Deprecated("Use ClientRelevantError instead.", replaceWith = ReplaceWith("ClientRelevantError"))
@CordaSerializable
interface RpcSerializableError