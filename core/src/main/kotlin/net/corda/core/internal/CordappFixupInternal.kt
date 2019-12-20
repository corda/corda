package net.corda.core.internal

import net.corda.core.DeleteForDJVM
import net.corda.core.node.services.AttachmentId

@DeleteForDJVM
interface CordappFixupInternal {
    fun fixupAttachmentIds(attachmentIds: Collection<AttachmentId>): Set<AttachmentId>
}
