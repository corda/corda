@file:JvmName("AttachmentConstants")
package net.corda.node.djvm

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.BrokenAttachmentException
import net.corda.core.contracts.ContractAttachment
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

private const val CONTRACT_IDX = 5
private const val ADDITIONAL_CONTRACT_IDX = 6
private const val UPLOADER_IDX = 7
private const val CONTRACT_SIGNER_KEYS_IDX = 8
private const val VERSION_IDX = 9

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
        @Suppress("unchecked_cast")
        return if (inputs == null) {
            unmodifiable(attachments)
        } else {
            var attachment: Attachment = SandboxAttachment(
                signerKeys = inputs[SIGNER_KEYS_IDX] as List<PublicKey>,
                size = inputs[SIZE_IDX] as Int,
                id = inputs[ID_IDX] as SecureHash,
                attachment = inputs[ATTACHMENT_IDX],
                streamer = inputs[STREAMER_IDX] as Function<in Any, out InputStream>
            )

            if (inputs.size > VERSION_IDX) {
                attachment = ContractAttachment.create(
                    attachment = attachment,
                    contract = inputs[CONTRACT_IDX] as String,
                    additionalContracts = (inputs[ADDITIONAL_CONTRACT_IDX] as Array<String>).toSet(),
                    uploader = inputs[UPLOADER_IDX] as? String,
                    signerKeys = inputs[CONTRACT_SIGNER_KEYS_IDX] as List<PublicKey>,
                    version = inputs[VERSION_IDX] as Int
                )
            }

            attachments.add(attachment)
            null
        }
    }
}

/**
 * This represents an [Attachment] from within the sandbox.
 */
private class SandboxAttachment(
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
