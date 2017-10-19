package net.corda.nodeapi.internal.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.util.DefaultClassResolver
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.node.services.statemachine.SessionData
import net.corda.testing.TestDependencyInjectionBase
import net.corda.testing.kryoSpecific
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.*

class SetsSerializationTest : TestDependencyInjectionBase() {
    private companion object {
        val javaEmptySetClass = Collections.emptySet<Any>().javaClass
    }

    @Test
    fun `check set can be serialized as root of serialization graph`() {
        assertEqualAfterRoundTripSerialization(emptySet<Int>())
        assertEqualAfterRoundTripSerialization(setOf(1))
        assertEqualAfterRoundTripSerialization(setOf(1, 2))
    }

    @Test
    fun `check set can be serialized as part of SessionData`() {
        run {
            val sessionData = SessionData(123, setOf(1).serialize())
            assertEqualAfterRoundTripSerialization(sessionData)
            assertEquals(setOf(1), sessionData.payload.deserialize())
        }
        run {
            val sessionData = SessionData(123, setOf(1, 2).serialize())
            assertEqualAfterRoundTripSerialization(sessionData)
            assertEquals(setOf(1, 2), sessionData.payload.deserialize())
        }
        run {
            val sessionData = SessionData(123, emptySet<Int>().serialize())
            assertEqualAfterRoundTripSerialization(sessionData)
            assertEquals(emptySet<Int>(), sessionData.payload.deserialize())
        }
    }

    @Test
    fun `check empty set serialises as Java emptySet`() = kryoSpecific("Checks Kryo header properties") {
        val nameID = 0
        val serializedForm = emptySet<Int>().serialize()
        val output = ByteArrayOutputStream().apply {
            write(KryoHeaderV0_1.bytes)
            write(DefaultClassResolver.NAME + 2)
            write(nameID)
            write(javaEmptySetClass.name.toAscii())
            write(Kryo.NOT_NULL.toInt())
        }
        assertArrayEquals(output.toByteArray(), serializedForm.bytes)
    }
}
