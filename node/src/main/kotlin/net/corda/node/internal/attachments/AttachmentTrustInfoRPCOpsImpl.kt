package net.corda.node.internal.attachments

import net.corda.core.internal.AttachmentTrustCalculator
import net.corda.core.internal.AttachmentTrustInfo
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.internal.messaging.AttachmentTrustInfoRPCOps

class AttachmentTrustInfoRPCOpsImpl(private val attachmentTrustCalculator: AttachmentTrustCalculator) : AttachmentTrustInfoRPCOps {

    override val protocolVersion: Int = PLATFORM_VERSION

    override val attachmentTrustInfos: List<AttachmentTrustInfo>
        get() {
            return attachmentTrustCalculator.calculateAllTrustInfo()
        }
}