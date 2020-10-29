package net.corda.serialization.internal

import net.corda.core.KeepForDJVM
import net.corda.core.crypto.DigestService
import net.corda.core.crypto.sha256
import net.corda.core.internal.AbstractAttachment

@KeepForDJVM
class GeneratedAttachment(val bytes: ByteArray, uploader: String?) : AbstractAttachment({ bytes }, uploader) {
    // TODO(iee): use default instead of sha2_256 once clarified what hash algorithm should be used for
    //            attachments. Does it also have impact on CorDapp jars? Would it be backward compatible?
    override val id = DigestService.sha2_256.hash(bytes)
}
