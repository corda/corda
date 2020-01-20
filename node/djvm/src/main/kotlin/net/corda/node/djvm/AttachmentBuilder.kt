@file:JvmName("AttachmentConstants")
package net.corda.node.djvm

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.BrokenAttachmentException
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import java.io.InputStream
import java.security.PublicKey
import java.util.Collections.unmodifiableList
import java.util.function.Function

private const val SIGNER_KEYS_IDX = 0
private const val SIZE_IDX = 1
private const val ID_IDX = 2
private const val ATTACHMENT_IDX = 3
private const val STREAMER_IDX = 4

class AttachmentBuilder : Function<Array<Any>?, List<Attachment>?> {
    private val attachments = mutableListOf<Attachment>()

    private fun <T> unmodifiable(list: List<T>): List<T> {
        return if (list.isEmpty()) {
            emptyList()
        } else {
            unmodifiableList(list)
        }
    }

    override fun apply(inputs: Array<Any>?): List<Attachment>? {
        return if (inputs == null) {
            unmodifiable(attachments)
        } else {
            @Suppress("unchecked_cast")
            attachments.add(SandboxAttachment(
                signerKeys = inputs[SIGNER_KEYS_IDX] as List<PublicKey>,
                size = inputs[SIZE_IDX] as Int,
                id = inputs[ID_IDX] as SecureHash,
                attachment = inputs[ATTACHMENT_IDX],
                streamer = inputs[STREAMER_IDX] as Function<in Any, out InputStream>
            ))
            null
        }
    }
}

/**
 * This represents an [Attachment] from within the sandbox.
 */
class SandboxAttachment(
    override val signerKeys: List<PublicKey>,
    override val size: Int,
    override val id: SecureHash,
    private val attachment: Any,
    private val streamer: Function<Any, out InputStream>
) : Attachment {
    @Suppress("OverridingDeprecatedMember")
    override val signers: List<Party> = emptyList()

    @Suppress("TooGenericExceptionCaught")
    override fun open(): InputStream {
        return try {
            streamer.apply(attachment)
        } catch (e: Exception) {
            throw BrokenAttachmentException(id, e.message, e)
        }
    }
}
