package net.corda.node.djvm

import net.corda.core.contracts.Attachment
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import java.io.InputStream
import java.security.PublicKey
import java.util.Collections.unmodifiableList
import java.util.function.Function

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
                signerKeys = inputs[0] as List<PublicKey>,
                size = inputs[1] as Int,
                id = inputs[2] as SecureHash,
                attachment = inputs[3],
                streamer = inputs[4] as Function<in Any, out InputStream>
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

    override fun open(): InputStream {
        return streamer.apply(attachment)
    }
}
