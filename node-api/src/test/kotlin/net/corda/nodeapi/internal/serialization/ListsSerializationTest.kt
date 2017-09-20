package net.corda.nodeapi.internal.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.util.DefaultClassResolver
import net.corda.core.serialization.*
import net.corda.node.services.statemachine.SessionData
import net.corda.testing.TestDependencyInjectionBase
import net.corda.testing.amqpSpecific
import org.assertj.core.api.Assertions
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.NotSerializableException
import java.nio.charset.StandardCharsets.*
import java.util.*

class ListsSerializationTest : TestDependencyInjectionBase() {
    private companion object {
        val javaEmptyListClass = Collections.emptyList<Any>().javaClass
    }

    @Test
    fun `check list can be serialized as root of serialization graph`() {
        assertEqualAfterRoundTripSerialization(emptyList<Int>())
        assertEqualAfterRoundTripSerialization(listOf(1))
        assertEqualAfterRoundTripSerialization(listOf(1, 2))
    }

    @Test
    fun `check list can be serialized as part of SessionData`() {
        run {
            val sessionData = SessionData(123, listOf(1))
            assertEqualAfterRoundTripSerialization(sessionData)
        }
        run {
            val sessionData = SessionData(123, listOf(1, 2))
            assertEqualAfterRoundTripSerialization(sessionData)
        }
        run {
            val sessionData = SessionData(123, emptyList<Int>())
            assertEqualAfterRoundTripSerialization(sessionData)
        }
    }

    @Test
    fun `check empty list serialises as Java emptyList`() {
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
    fun `check throws for forbidden declared type`() = amqpSpecific<ListsSerializationTest>("Such exceptions are not expected in Kryo mode.") {
        val payload = ArrayList<Int>()
        payload.add(1)
        payload.add(2)
        val wrongPayloadType = WrongPayloadType(payload)
        Assertions.assertThatThrownBy { wrongPayloadType.serialize() }
                .isInstanceOf(NotSerializableException::class.java).hasMessageContaining("Cannot derive collection type for declaredType")
    }
}

internal inline fun<reified T : Any> assertEqualAfterRoundTripSerialization(obj: T) {

    val serializedForm: SerializedBytes<T> = obj.serialize()
    val deserializedInstance = serializedForm.deserialize()

    assertEquals(obj, deserializedInstance)
}

internal fun String.toAscii(): ByteArray = toByteArray(US_ASCII).apply {
    this[lastIndex] = (this[lastIndex] + 0x80).toByte()
}
