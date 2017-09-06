package net.corda.nodeapi.internal.serialization

import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.node.services.statemachine.SessionData
import net.corda.testing.TestDependencyInjectionBase
import net.corda.testing.amqpSpecific
import org.assertj.core.api.Assertions
import org.junit.Test
import java.io.NotSerializableException
import kotlin.test.assertEquals

class ListsSerializationTest : TestDependencyInjectionBase() {

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