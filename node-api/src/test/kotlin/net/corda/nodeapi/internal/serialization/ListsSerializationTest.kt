package net.corda.nodeapi.internal.serialization

import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.testing.TestDependencyInjectionBase
import net.corda.testing.kryoSpecific
import org.junit.Test
import kotlin.test.assertEquals

class ListsSerializationTest : TestDependencyInjectionBase() {

    @Test
    fun `check list can be serialized as root of serialization graph`() = kryoSpecific<ListsSerializationTest>("AMQP doesn't support lists as the root of serialization graph"){
        assertEqualAfterRoundTripSerialization(listOf(1))
        assertEqualAfterRoundTripSerialization(listOf(1, 2))
    }

    private inline fun<reified T : Any> assertEqualAfterRoundTripSerialization(obj: T) {

        val serializedForm: SerializedBytes<T> = obj.serialize()
        val deserializedInstance = serializedForm.deserialize()

        assertEquals(obj, deserializedInstance)
    }
}