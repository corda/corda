package net.corda.djvm.serialization

import greymalkin.ExternalData
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.djvm.serialization.SandboxType.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.io.NotSerializableException
import java.util.function.Function

@ExtendWith(LocalSerialization::class)
class DeserializeClassTest : TestBase(KOTLIN) {
    @Test
    fun `test deserializing existing class`() {
        val myClass = ExternalData::class.java
        val data = myClass.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxInstant = data.deserializeFor(classLoader)

            val executor = createExecutorFor(classLoader)
            val result = executor.apply(
                classLoader.loadClassForSandbox(ShowClass::class.java).newInstance(),
                sandboxInstant
            ) ?: fail("Result cannot be null")

            assertEquals("sandbox.${myClass.name}", result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    @Test
    fun `test deserializing missing class`() {
        // The DJVM will refuse to find this class because it belongs to net.corda.djvm.**.
        val myClass = VeryEvilData::class.java
        val data = myClass.serialize()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val ex = assertThrows<NotSerializableException>{ data.deserializeFor(classLoader) }
            assertThat(ex)
                .isExactlyInstanceOf(NotSerializableException::class.java)
                .hasMessageContaining("VeryEvilData was not found by the node,")
        }
    }
}

class ShowClass : Function<Class<*>, String> {
    override fun apply(type: Class<*>): String {
        return type.name
    }
}
