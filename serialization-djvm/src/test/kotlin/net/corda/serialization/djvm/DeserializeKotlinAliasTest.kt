package net.corda.serialization.djvm

import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.AttachmentId
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializeKotlinAliasTest : TestBase(KOTLIN) {
    @Test
	fun `test deserializing kotlin alias`() {
        val attachmentId = SecureHash.allOnesHash
        val data = attachmentId.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxAttachmentId = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showAlias = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowAlias::class.java)
            val result = showAlias.apply(sandboxAttachmentId) ?: fail("Result cannot be null")

            assertEquals(ShowAlias().apply(attachmentId), result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    class ShowAlias : Function<AttachmentId, String> {
        override fun apply(id: AttachmentId): String {
            return id.toString()
        }
    }

    @Test
	fun `test deserializing data with kotlin alias`() {
        val attachment = AttachmentData(SecureHash.allOnesHash)
        val data = attachment.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxAttachment = data.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showAliasData = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowAliasData::class.java)
            val result = showAliasData.apply(sandboxAttachment) ?: fail("Result cannot be null")

            assertEquals(ShowAliasData().apply(attachment), result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    class ShowAliasData: Function<AttachmentData, String> {
        override fun apply(data: AttachmentData): String {
            return data.toString()
        }
    }
}

@CordaSerializable
data class AttachmentData(val id: AttachmentId)
