package net.corda.serialization.djvm

import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.Date
import java.util.UUID

@ExtendWith(LocalSerialization::class)
class DeserializePrimitivesTest : TestBase(KOTLIN) {
    @Test
	fun `test naked uuid`() {
        val uuid = UUID.randomUUID()
        val data = uuid.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxUUID = data.deserializeFor(classLoader)
            assertEquals(uuid.toString(), sandboxUUID.toString())
            assertEquals("sandbox.${uuid::class.java.name}", sandboxUUID::class.java.name)
        }
    }

    @Test
	fun `test wrapped uuid`() {
        val uuid = WrappedUUID(UUID.randomUUID())
        val data = uuid.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxUUID = data.deserializeFor(classLoader)
            assertEquals(uuid.toString(), sandboxUUID.toString())
            assertEquals("sandbox.${uuid::class.java.name}", sandboxUUID::class.java.name)
        }
    }

    @Test
	fun `test naked date`() {
        val now = Date()
        val data = now.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxNow = data.deserializeFor(classLoader)
            assertEquals(now.toString(), sandboxNow.toString())
            assertEquals("sandbox.${now::class.java.name}", sandboxNow::class.java.name)
        }
    }

    @Test
	fun `test wrapped date`() {
        val now = WrappedDate(Date())
        val data = now.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxNow = data.deserializeFor(classLoader)
            assertEquals(now.toString(), sandboxNow.toString())
            assertEquals("sandbox.${now::class.java.name}", sandboxNow::class.java.name)
        }
    }
}

@CordaSerializable
data class WrappedUUID(val uuid: UUID)

@CordaSerializable
data class WrappedDate(val date: Date)
