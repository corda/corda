package net.corda.nodeapi.internal.serialization

import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.node.services.statemachine.SessionData
import net.corda.testing.TestDependencyInjectionBase
import org.junit.Test
import kotlin.test.assertEquals

class ListsSerializationTest : TestDependencyInjectionBase() {

    @Test
    fun `check list can be serialized as root of serialization graph`() {
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

    private inline fun<reified T : Any> assertEqualAfterRoundTripSerialization(obj: T) {

        val serializedForm: SerializedBytes<T> = obj.serialize()
        val deserializedInstance = serializedForm.deserialize()

        assertEquals(obj, deserializedInstance)
    }
}