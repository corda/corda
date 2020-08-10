package net.corda.serialization.djvm

import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.SandboxType.KOTLIN
import net.corda.serialization.internal.amqp.CompositeType
import net.corda.serialization.internal.amqp.DeserializationInput
import net.corda.serialization.internal.amqp.RestrictedType
import net.corda.serialization.internal.amqp.TypeNotation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.function.Function

/**
 * Corda 4.4 briefly serialised [Enum] values using [Enum.toString] rather
 * than [Enum.name]. We need to be able to deserialise these values now
 * that the bug has been fixed.
 */
@ExtendWith(LocalSerialization::class)
class DeserializeRemoteCustomisedEnumTest : TestBase(KOTLIN) {
    @ParameterizedTest
    @EnumSource(Broken::class)
    fun `test deserialize broken enum with custom toString`(broken: Broken) {
        val workingData = broken.serialize().rewriteEnumAsWorking()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxWorkingClass = classLoader.toSandboxClass(Working::class.java)
            val sandboxWorkingValue = workingData.deserializeFor(classLoader)
            assertThat(sandboxWorkingValue::class.java).isSameAs(sandboxWorkingClass)
            assertThat(sandboxWorkingValue.toString()).isEqualTo(broken.label)
        }
    }

    /**
     * This function rewrites the [SerializedBytes] for a naked [Broken] object
     * into the [SerializedBytes] that Corda 4.4 would generate for an equivalent
     * [Working] object.
     */
    @Suppress("unchecked_cast")
    private fun SerializedBytes<Broken>.rewriteEnumAsWorking(): SerializedBytes<Working> {
        val envelope = DeserializationInput.getEnvelope(this).apply {
            val restrictedType = schema.types[0] as RestrictedType
            (schema.types as MutableList<TypeNotation>)[0] = restrictedType.copy(
                name = toWorking(restrictedType.name)
            )
        }
        return SerializedBytes(envelope.write())
    }

    @ParameterizedTest
    @EnumSource(Broken::class)
    fun `test deserialize composed broken enum with custom toString`(broken: Broken) {
        val brokenContainer = BrokenContainer(broken)
        val workingData = brokenContainer.serialize().rewriteContainerAsWorking()

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))

            val sandboxContainer = workingData.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val showWorkingData = taskFactory.compose(classLoader.createSandboxFunction()).apply(ShowWorkingData::class.java)
            val result = showWorkingData.apply(sandboxContainer) ?: fail("Result cannot be null")

            assertEquals("Working: label='${broken.label}', ordinal='${broken.ordinal}'", result.toString())
            assertEquals(SANDBOX_STRING, result::class.java.name)
        }
    }

    class ShowWorkingData : Function<WorkingContainer, String> {
        override fun apply(input: WorkingContainer): String {
            return with(input) {
                "Working: label='${value.label}', ordinal='${value.ordinal}'"
            }
        }
    }

    /**
     * This function rewrites the [SerializedBytes] for a [Broken]
     * property that has been composed inside a [BrokenContainer].
     * It will generate the [SerializedBytes] that Corda 4.4 would
     * generate for an equivalent [WorkingContainer].
     */
    @Suppress("unchecked_cast")
    private fun SerializedBytes<BrokenContainer>.rewriteContainerAsWorking(): SerializedBytes<WorkingContainer> {
        val envelope = DeserializationInput.getEnvelope(this).apply {
            val compositeType = schema.types[0] as CompositeType
            (schema.types as MutableList<TypeNotation>)[0] = compositeType.copy(
                name = toWorking(compositeType.name),
                fields = compositeType.fields.map { it.copy(type = toWorking(it.type)) }
            )
            val restrictedType = schema.types[1] as RestrictedType
            (schema.types as MutableList<TypeNotation>)[1] = restrictedType.copy(
                name = toWorking(restrictedType.name)
            )
        }
        return SerializedBytes(envelope.write())
    }

    private fun toWorking(oldName: String): String = oldName.replace("Broken", "Working")

    /**
     * This is the enumerated type, as it actually exist.
     */
    @Suppress("unused")
    enum class Working(val label: String) {
        ZERO("None"),
        ONE("Once"),
        TWO("Twice");

        @Override
        override fun toString(): String = label
    }

    @CordaSerializable
    data class WorkingContainer(val value: Working)

    /**
     * This represents a broken serializer's view of the [Working]
     * enumerated type, which would serialize using [Enum.toString]
     * rather than [Enum.name].
     */
    @Suppress("unused")
    @CordaSerializable
    enum class Broken(val label: String) {
        None("None"),
        Once("Once"),
        Twice("Twice");

        @Override
        override fun toString(): String = label
    }

    @CordaSerializable
    data class BrokenContainer(val value: Broken)
}
