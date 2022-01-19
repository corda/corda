package net.corda.nodeapi.exceptions

import net.corda.core.CordaRuntimeException
import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.CordaSerializable

/**
 * Thrown to indicate that an attachment was already uploaded to a Corda node.
 */
class DuplicateAttachmentException(attachmentHash: String) :
        java.nio.file.FileAlreadyExistsException(attachmentHash),
        @Suppress("DEPRECATION") net.corda.core.ClientRelevantError

/**
 * Thrown to indicate that a flow was not designed for RPC and should be started from an RPC client.
 */
class NonRpcFlowException(logicType: Class<*>) :
        IllegalArgumentException("${logicType.name} was not designed for RPC"),
        @Suppress("DEPRECATION") net.corda.core.ClientRelevantError

class OutdatedNetworkParameterHashException(old: SecureHash, new: SecureHash) :
        CordaRuntimeException(TEMPLATE.format(old, new)),
        @Suppress("DEPRECATION") net.corda.core.ClientRelevantError
{

    private companion object {
        private const val TEMPLATE = "Refused to accept parameters with hash %s because network map advertises update with hash %s. Please check newest version"
    }
}

/**
 * Thrown to indicate that the command was rejected by the node, typically due to a special temporary mode.
 */
class RejectedCommandException(message: String) :
        CordaRuntimeException(message),
        @Suppress("DEPRECATION") net.corda.core.ClientRelevantError

/**
 * Thrown to indicate that the command was rejected by the node, typically due to a special temporary mode.
 */
class MissingAttachmentException(message: String) :
        CordaRuntimeException(message)

/**
 * Allows an implementing [Throwable] to be propagated to RPC clients.
 */
@Deprecated("Use ClientRelevantError instead.", replaceWith = ReplaceWith("ClientRelevantError"))
@CordaSerializable
interface RpcSerializableError