package net.corda.core.internal

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.ContractAttachment

interface InternalAttachment : Attachment {
    /**
     * The version of the Kotlin metadata, if this attachment has one. See `kotlinx.metadata.jvm.JvmMetadataVersion` for more information on
     * how this maps to the Kotlin language version.
     */
    val kotlinMetadataVersion: String?
}

/**
 * Because [ContractAttachment] is public API, we can't make it implement [InternalAttachment] without also leaking it out.
 *
 * @see InternalAttachment.kotlinMetadataVersion
 */
val Attachment.kotlinMetadataVersion: String? get() {
    var attachment = this
    while (true) {
        when (attachment) {
            is InternalAttachment -> return attachment.kotlinMetadataVersion
            is ContractAttachment -> attachment = attachment.attachment
            else -> return null
        }
    }
}
