package net.corda.serialization.internal

import net.corda.core.Deterministic
import net.corda.core.crypto.sha256
import net.corda.core.internal.AbstractAttachment

@Deterministic
class GeneratedAttachment(val bytes: ByteArray) : AbstractAttachment({ bytes }) {
    override val id = bytes.sha256()
}
