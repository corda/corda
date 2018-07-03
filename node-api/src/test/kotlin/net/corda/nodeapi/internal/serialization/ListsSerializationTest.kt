package net.corda.nodeapi.internal.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.util.DefaultClassResolver
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.node.services.statemachine.DataSessionMessage
import net.corda.nodeapi.internal.serialization.amqp.DeserializationInput
import net.corda.nodeapi.internal.serialization.amqp.Envelope
import net.corda.nodeapi.internal.serialization.kryo.KryoHeaderV0_1
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.internal.amqpSpecific
import net.corda.testing.internal.kryoSpecific
import org.assertj.core.api.Assertions
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.NotSerializableException
import java.nio.charset.StandardCharsets.US_ASCII
import java.util.*

class ListsSerializationTest {
    private companion object {
        val javaEmptyListClass = Collections.emptyList<Any>().javaClass

        fun <T : Any> verifyEnvelope(serBytes: SerializedBytes<T>, envVerBody: (Envelope) -> Unit) =
                amqpSpecific("AMQP specific envelope verification") {
                    val envelope = DeserializationInput.getEnvelope(serBytes)
                    envVerBody(envelope)
                }
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    @Test
    fun `check list can be serialized as root of serialization graph`() {
        assertEqualAfterRoundTripSerialization(emptyList<Int>())
        assertEqualAfterRoundTripSerialization(listOf(1))
        assertEqualAfterRoundTripSerialization(listOf(1, 2))
    }

    @Test
    fun `check list can be serialized as part of SessionData`() {
        run {
            val sessionData = DataSessionMessage(listOf(1).serialize())
            assertEqualAfterRoundTripSerialization(sessionData)
            assertEquals(listOf(1), sessionData.payload.deserialize())
        }
        run {
            val sessionData = DataSessionMessage(listOf(1, 2).serialize())
            assertEqualAfterRoundTripSerialization(sessionData)
            assertEquals(listOf(1, 2), sessionData.payload.deserialize())
        }
        run {
            val sessionData = DataSessionMessage(emptyList<Int>().serialize())
            assertEqualAfterRoundTripSerialization(sessionData)
            assertEquals(emptyList<Int>(), sessionData.payload.deserialize())
        }
    }

    @Test
    fun `check empty list serialises as Java emptyList`() = kryoSpecific("Kryo specific test") {
        val nameID = 0
        val serializedForm = emptyList<Int>().serialize()
        val output = ByteArrayOutputStream().apply {
            write(KryoHeaderV0_1.bytes)
            write(DefaultClassResolver.NAME + 2)
            write(nameID)
            write(javaEmptyListClass.name.toAscii())
            write(Kryo.NOT_NULL.toInt())
        }
        assertArrayEquals(output.toByteArray(), serializedForm.bytes)
    }

    @CordaSerializable
    data class WrongPayloadType(val payload: ArrayList<Int>)

    @Test
    fun `check throws for forbidden declared type`() = amqpSpecific("Such exceptions are not expected in Kryo mode.") {
        val payload = ArrayList<Int>()
        payload.add(1)
        payload.add(2)
        val wrongPayloadType = WrongPayloadType(payload)
        Assertions.assertThatThrownBy { wrongPayloadType.serialize() }
                .isInstanceOf(NotSerializableException::class.java).hasMessageContaining("Cannot derive collection type for declaredType")
    }

    @CordaSerializable
    interface Parent

    data class Child(val value: Int) : Parent

    @CordaSerializable
    data class CovariantContainer<out T : Parent>(val payload: List<T>)

    @Test
    fun `check covariance`() {
        val payload = ArrayList<Child>()
        payload.add(Child(1))
        payload.add(Child(2))
        val container = CovariantContainer(payload)

        fun verifyEnvelopeBody(envelope: Envelope) {
            envelope.schema.types.single { typeNotation -> typeNotation.name == java.util.List::class.java.name + "<?>" }
        }

        assertEqualAfterRoundTripSerialization(container, { bytes -> verifyEnvelope(bytes, ::verifyEnvelopeBody) })
    }
}

internal inline fun <reified T : Any> assertEqualAfterRoundTripSerialization(obj: T, noinline streamValidation: ((SerializedBytes<T>) -> Unit)? = null) {

    val serializedForm: SerializedBytes<T> = obj.serialize()
    streamValidation?.invoke(serializedForm)
    val deserializedInstance = serializedForm.deserialize()

    assertEquals(obj, deserializedInstance)
}

internal fun String.toAscii(): ByteArray = toByteArray(US_ASCII).apply {
    this[lastIndex] = (this[lastIndex] + 0x80).toByte()
}
