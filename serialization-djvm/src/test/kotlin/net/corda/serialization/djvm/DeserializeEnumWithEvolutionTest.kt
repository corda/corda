package net.corda.serialization.djvm

import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.CordaSerializationTransformEnumDefault
import net.corda.core.serialization.CordaSerializationTransformEnumDefaults
import net.corda.core.serialization.CordaSerializationTransformRename
import net.corda.core.serialization.CordaSerializationTransformRenames
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.djvm.EvolvedEnum.ONE
import net.corda.serialization.djvm.EvolvedEnum.TWO
import net.corda.serialization.djvm.EvolvedEnum.THREE
import net.corda.serialization.djvm.EvolvedEnum.FOUR
import net.corda.serialization.djvm.OriginalEnum.One
import net.corda.serialization.djvm.OriginalEnum.Two
import net.corda.serialization.djvm.SandboxType.KOTLIN
import net.corda.serialization.internal.amqp.CompositeType
import net.corda.serialization.internal.amqp.DeserializationInput
import net.corda.serialization.internal.amqp.RestrictedType
import net.corda.serialization.internal.amqp.Transform
import net.corda.serialization.internal.amqp.TransformTypes
import net.corda.serialization.internal.amqp.TypeNotation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import java.util.EnumMap
import java.util.function.Function
import java.util.stream.Stream

@ExtendWith(LocalSerialization::class)
class DeserializeEnumWithEvolutionTest : TestBase(KOTLIN) {
    class EvolutionArgumentProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> {
            return Stream.of(
                Arguments.of(ONE, One),
                Arguments.of(TWO, Two),
                Arguments.of(THREE, One),
                Arguments.of(FOUR, Two)
            )
        }
    }

    private fun String.devolve() = replace("Evolved", "Original")

    private fun devolveType(type: TypeNotation): TypeNotation {
        return when (type) {
            is CompositeType -> type.copy(
                name = type.name.devolve(),
                fields = type.fields.map { it.copy(type = it.type.devolve()) }
            )
            is RestrictedType -> type.copy(name = type.name.devolve())
            else -> type
        }
    }

    private fun SerializedBytes<*>.devolve(context: SerializationContext): SerializedBytes<Any> {
        val envelope = DeserializationInput.getEnvelope(this, context.encodingWhitelist).apply {
            val schemaTypes = schema.types.map(::devolveType)
            with(schema.types as MutableList<TypeNotation>) {
                clear()
                addAll(schemaTypes)
            }

            val transforms = transformsSchema.types.asSequence().associateTo(LinkedHashMap()) {
                it.key.devolve() to it.value
            }
            with(transformsSchema.types as MutableMap<String, EnumMap<TransformTypes, MutableList<Transform>>>) {
                clear()
                putAll(transforms)
            }
        }
        return SerializedBytes(envelope.write())
    }

    @ParameterizedTest
    @ArgumentsSource(EvolutionArgumentProvider::class)
    fun `test deserialising evolved enum`(value: EvolvedEnum, expected: OriginalEnum) {
        val context = (_contextSerializationEnv.get() ?: fail("No serialization environment!")).p2pContext

        val evolvedData = value.serialize()
        val originalData = evolvedData.devolve(context)

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))
            val sandboxOriginal = originalData.deserializeFor(classLoader)
            assertEquals("sandbox." + OriginalEnum::class.java.name, sandboxOriginal::class.java.name)
            assertEquals(expected.toString(), sandboxOriginal.toString())
        }
    }

    @ParameterizedTest
    @ArgumentsSource(EvolutionArgumentProvider::class)
    fun `test deserialising data with evolved enum`(value: EvolvedEnum, expected: OriginalEnum) {
        val context = (_contextSerializationEnv.get() ?: fail("No serialization environment!")).p2pContext

        val evolvedData = EvolvedData(value).serialize()
        val originalData = evolvedData.devolve(context)

        sandbox {
            _contextSerializationEnv.set(createSandboxSerializationEnv(classLoader))
            val sandboxOriginal = originalData.deserializeFor(classLoader)

            val taskFactory = classLoader.createRawTaskFactory()
            val result = taskFactory.compose(classLoader.createSandboxFunction())
                .apply(ShowOriginalData::class.java)
                .apply(sandboxOriginal) ?: fail("Result cannot be null")
            assertThat(result.toString())
                .isEqualTo(ShowOriginalData().apply(OriginalData(expected)))
        }
    }

    class ShowOriginalData : Function<OriginalData, String> {
        override fun apply(input: OriginalData): String {
            return with(input) {
                "Name='${value.name}', Ordinal='${value.ordinal}'"
            }
        }
    }
}

@CordaSerializable
enum class OriginalEnum {
    One,
    Two
}

@CordaSerializable
data class OriginalData(val value: OriginalEnum)

@CordaSerializable
@CordaSerializationTransformRenames(
    CordaSerializationTransformRename(from = "One", to = "ONE"),
    CordaSerializationTransformRename(from = "Two", to = "TWO")
)
@CordaSerializationTransformEnumDefaults(
    CordaSerializationTransformEnumDefault(new = "THREE", old = "One"),
    CordaSerializationTransformEnumDefault(new = "FOUR", old = "Two")
)
enum class EvolvedEnum {
    ONE,
    TWO,
    THREE,
    FOUR
}

@CordaSerializable
data class EvolvedData(val value: EvolvedEnum)