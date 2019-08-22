package net.corda.core.internal.messaging

import net.corda.core.internal.AttachmentTrustRoot
import net.corda.core.messaging.CordaRPCOps

/**
 * Contains internal RPC functions that should not be publicly exposed in [CordaRPCOps]
 */
interface InternalCordaRPCOps : CordaRPCOps {

    /** Dump all the current flow checkpoints as JSON into a zip file in the node's log directory. */
    fun dumpCheckpoints()

    /** Get the attachment trust roots */
    fun getAttachmentTrustRoots(): List<AttachmentTrustRoot>
}