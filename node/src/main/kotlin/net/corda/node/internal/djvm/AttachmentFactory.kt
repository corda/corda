package net.corda.node.internal.djvm

import net.corda.core.contracts.Attachment
import net.corda.core.contracts.ContractAttachment
import net.corda.core.serialization.serialize
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.node.djvm.AttachmentBuilder
import java.util.function.Function

class AttachmentFactory(
    classLoader: SandboxClassLoader,
    private val taskFactory: Function<Class<out Function<*,*>>, out Function<in Any?, out Any?>>,
    private val sandboxBasicInput: Function<in Any?, out Any?>,
    private val serializer: Serializer
) {
    private val sandboxOpenAttachment: Function<in Attachment, out Any?> = classLoader.createForImport(
        Function(Attachment::open).andThen(sandboxBasicInput)
    )

    fun toSandbox(attachments: List<Attachment>): Any? {
        val builder = taskFactory.apply(AttachmentBuilder::class.java)
        for (attachment in attachments) {
            builder.apply(generateArgsFor(attachment))
        }
        return builder.apply(null)
    }

    private fun generateArgsFor(attachment: Attachment): Array<Any?> {
        val signerKeys = serializer.deserialize(attachment.signerKeys.serialize())
        val id = serializer.deserialize(attachment.id.serialize())
        val size = sandboxBasicInput.apply(attachment.size)
        return if (attachment is ContractAttachment) {
            val underlyingAttachment = attachment.attachment
            arrayOf(
                serializer.deserialize(underlyingAttachment.signerKeys.serialize()),
                size, id,
                underlyingAttachment,
                sandboxOpenAttachment,
                sandboxBasicInput.apply(attachment.contract),
                sandboxBasicInput.apply(attachment.additionalContracts.toTypedArray()),
                sandboxBasicInput.apply(attachment.uploader),
                signerKeys,
                sandboxBasicInput.apply(attachment.version)
            )
        } else {
            arrayOf(signerKeys, size, id, attachment, sandboxOpenAttachment)
        }
    }
}
