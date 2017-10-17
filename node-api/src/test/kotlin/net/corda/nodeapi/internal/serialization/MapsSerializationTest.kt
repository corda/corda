package net.corda.nodeapi.internal.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.util.DefaultClassResolver
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.node.services.statemachine.SessionData
import net.corda.testing.TestDependencyInjectionBase
import net.corda.testing.amqpSpecific
import net.corda.testing.kryoSpecific
import org.assertj.core.api.Assertions
import org.bouncycastle.asn1.x500.X500Name
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.test.assertEquals

class MapsSerializationTest : TestDependencyInjectionBase() {
    private companion object {
        val javaEmptyMapClass = Collections.emptyMap<Any, Any>().javaClass
        val smallMap = mapOf("foo" to "bar", "buzz" to "bull")
    }

    @Test
    fun `check EmptyMap serialization`() = amqpSpecific("kotlin.collections.EmptyMap is not enabled for Kryo serialization") {
        assertEqualAfterRoundTripSerialization(emptyMap<Any, Any>())
    }

    @Test
    fun `check Map can be root of serialization graph`() {
        assertEqualAfterRoundTripSerialization(smallMap)
    }

    @Test
    fun `check list can be serialized as part of SessionData`() {
        val sessionData = SessionData(123, smallMap.serialize())
        assertEqualAfterRoundTripSerialization(sessionData)
        assertEquals(smallMap, sessionData.payload.deserialize())
    }

    @CordaSerializable
    data class WrongPayloadType(val payload: HashMap<String, String>)

    @Test
    fun `check throws for forbidden declared type`() = amqpSpecific("Such exceptions are not expected in Kryo mode.") {
        val payload = HashMap<String, String>(smallMap)
        val wrongPayloadType = WrongPayloadType(payload)
        Assertions.assertThatThrownBy { wrongPayloadType.serialize() }
                .isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining(
                "Map type class java.util.HashMap is unstable under iteration. Suggested fix: use java.util.LinkedHashMap instead.")
    }

    @CordaSerializable
    data class MyKey(val keyContent: Double)

    @CordaSerializable
    data class MyValue(val valueContent: X500Name)

    @Test
    fun `check map serialization works with custom types`() {
        val myMap = mapOf(
                MyKey(1.0) to MyValue(X500Name("CN=one")),
                MyKey(10.0) to MyValue(X500Name("CN=ten")))
        assertEqualAfterRoundTripSerialization(myMap)
    }

    @Test
    fun `check empty map serialises as Java emptyMap`() = kryoSpecific("Specifically checks Kryo serialization") {
        val nameID = 0
        val serializedForm = emptyMap<Int, Int>().serialize()
        val output = ByteArrayOutputStream().apply {
            write(KryoHeaderV0_1.bytes)
            write(DefaultClassResolver.NAME + 2)
            write(nameID)
            write(javaEmptyMapClass.name.toAscii())
            write(Kryo.NOT_NULL.toInt())
        }
        assertArrayEquals(output.toByteArray(), serializedForm.bytes)
    }
}