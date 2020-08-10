package net.corda.core.internal.messaging

import net.corda.core.internal.AttachmentTrustInfo
import net.corda.core.messaging.RPCOps

interface AttachmentTrustInfoRPCOps : RPCOps {
    /**
     * Get all the attachment trust information
     */
    val attachmentTrustInfos: List<AttachmentTrustInfo>
}