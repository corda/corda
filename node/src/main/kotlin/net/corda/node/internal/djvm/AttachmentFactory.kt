package net.corda.node.internal.djvm

import net.corda.core.contracts.Attachment
import net.corda.core.serialization.serialize
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.node.djvm.AttachmentBuilder
import java.util.function.Function

class AttachmentFactory(
    private val classLoader: SandboxClassLoader,
    private val taskFactory: Function<in Any, out Function<in Any?, out Any?>>,
    private val sandboxBasicInput: Function<in Any?, out Any?>,
    private val serializer: Serializer
) {
    private val sandboxOpenAttachment: Function<in Attachment, out Any?> = classLoader.createForImport(
        Function(Attachment::open).andThen(sandboxBasicInput)
    )

    fun toSandbox(attachments: List<Attachment>): Any? {
        val builder = classLoader.createTaskFor(taskFactory, AttachmentBuilder::class.java)
        for (attachment in attachments) {
            builder.apply(arrayOf(
                serializer.deserialize(attachment.signerKeys.serialize()),
                sandboxBasicInput.apply(attachment.size),
                serializer.deserialize(attachment.id.serialize()),
                attachment,
                sandboxOpenAttachment
            ))
        }
        return builder.apply(null)
    }
}
