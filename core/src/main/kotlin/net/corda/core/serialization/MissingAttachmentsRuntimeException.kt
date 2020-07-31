package net.corda.core.serialization

import net.corda.core.CordaRuntimeException
import net.corda.core.KeepForDJVM
import net.corda.core.node.services.AttachmentId

@KeepForDJVM
@CordaSerializable
class MissingAttachmentsRuntimeException(val ids: List<AttachmentId>, message: String?, cause: Throwable?)
    : CordaRuntimeException(message, cause) {

    @Suppress("unused")
    constructor(ids: List<AttachmentId>, message: String?) : this(ids, message, null)

    @Suppress("unused")
    constructor(ids: List<AttachmentId>) : this(ids, null, null)
}
