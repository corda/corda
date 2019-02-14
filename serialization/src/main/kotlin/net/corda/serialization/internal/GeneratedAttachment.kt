package net.corda.serialization.internal

import net.corda.core.KeepForDJVM
import net.corda.core.crypto.sha256
import net.corda.core.internal.AbstractAttachment

@KeepForDJVM
class GeneratedAttachment(val bytes: ByteArray, uploader: String?) : AbstractAttachment({ bytes }, uploader) {
    override val id = bytes.sha256()
}
