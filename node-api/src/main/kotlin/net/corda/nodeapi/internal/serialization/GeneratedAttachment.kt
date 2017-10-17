package net.corda.nodeapi.internal.serialization

import net.corda.core.crypto.sha256
import net.corda.core.internal.AbstractAttachment

class GeneratedAttachment(val bytes: ByteArray) : AbstractAttachment({ bytes }) {
    override val id = bytes.sha256()
}
