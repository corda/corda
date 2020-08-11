package net.corda.serialization.internal.amqp

import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationContext.UseCase.Testing
import net.corda.core.serialization.SerializedBytes
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.SerializationContextImpl
import net.corda.serialization.internal.amqp.testutils.TestSerializationOutput
import net.corda.serialization.internal.amqp.testutils.testDefaultFactory
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Corda 4.4 briefly serialised [Enum] values using [Enum.toString] rather
 * than [Enum.name]. We need to be able to deserialise these values now
 * that the bug has been fixed.
 */
class EnumToStringFallbackTest {
    private lateinit var serializationOutput: TestSerializationOutput

    private fun createTestContext(): SerializationContext = SerializationContextImpl(
        preferredSerializationVersion = amqpMagic,
        deserializationClassLoader = ClassLoader.getSystemClassLoader(),
        whitelist = AllWhitelist,
        properties = emptyMap(),
        objectReferencesEnabled = false,
        useCase = Testing,
        encoding = null
    )

    @Before
    fun setup() {
        serializationOutput = TestSerializationOutput(verbose = false)
    }

    @Test(timeout = 300_000)
    fun deserializeEnumWithToString() {
        val broken = BrokenContainer(Broken.Twice)
        val brokenData = serializationOutput.serialize(broken, createTestContext())
        val workingData = brokenData.rewriteAsWorking()
        val working = DeserializationInput(testDefaultFactory()).deserialize(workingData, createTestContext())
        assertEquals(Working.TWO, working.value)
    }

    /**
     * This function rewrites the [SerializedBytes] for a [Broken]
     * property that has been composed inside a [BrokenContainer].
     * It will generate the [SerializedBytes] that Corda 4.4 would
     * generate for an equivalent [WorkingContainer].
     */
    @Suppress("unchecked_cast")
    private fun SerializedBytes<BrokenContainer>.rewriteAsWorking(): SerializedBytes<WorkingContainer> {
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
    enum class Working(private val label: String) {
        ZERO("None"),
        ONE("Once"),
        TWO("Twice");

        @Override
        override fun toString(): String = label
    }

    /**
     * This represents a broken serializer's view of the [Working]
     * enumerated type, which would serialize using [Enum.toString]
     * rather than [Enum.name].
     */
    @Suppress("unused")
    enum class Broken(private val label: String) {
        None("None"),
        Once("Once"),
        Twice("Twice");

        @Override
        override fun toString(): String = label
    }

    data class WorkingContainer(val value: Working)
    data class BrokenContainer(val value: Broken)
}