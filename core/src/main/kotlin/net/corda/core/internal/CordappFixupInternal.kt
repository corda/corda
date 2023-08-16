package net.corda.core.internal

import net.corda.core.node.services.AttachmentId

interface CordappFixupInternal {
    fun fixupAttachmentIds(attachmentIds: Collection<AttachmentId>): Set<AttachmentId>
}
