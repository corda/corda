package net.corda.serialization.internal

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.util.DefaultClassResolver
import net.corda.core.identity.CordaX500Name
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.node.serialization.kryo.kryoMagic
import net.corda.node.services.statemachine.DataSessionMessage
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.internal.amqpSpecific
import net.corda.testing.internal.kryoSpecific
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Assert.assertArrayEquals
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.test.assertEquals

class MapsSerializationTest {
    private companion object {
        val javaEmptyMapClass = Collections.emptyMap<Any, Any>().javaClass
        val smallMap = mapOf("foo" to "bar", "buzz" to "bull")
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

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
        val sessionData = DataSessionMessage(smallMap.serialize())
        assertEqualAfterRoundTripSerialization(sessionData)
        assertEquals(smallMap, sessionData.payload.deserialize())
    }

    @CordaSerializable
    data class WrongPayloadType(val payload: HashMap<String, String>)

    @Test
    fun `check throws for forbidden declared type`() = amqpSpecific("Such exceptions are not expected in Kryo mode.") {
        val payload = HashMap<String, String>(smallMap)
        val wrongPayloadType = WrongPayloadType(payload)
        assertThatThrownBy { wrongPayloadType.serialize() }
                .isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining(
                        "Map type class java.util.HashMap is unstable under iteration. Suggested fix: use java.util.LinkedHashMap instead.")
    }

    @CordaSerializable
    data class MyKey(val keyContent: Double)

    @CordaSerializable
    data class MyValue(val valueContent: CordaX500Name)

    @Test
    fun `check map serialization works with custom types`() {
        val myMap = mapOf(
                MyKey(1.0) to MyValue(CordaX500Name("OOO", "LLL", "CC")),
                MyKey(10.0) to MyValue(CordaX500Name("OO", "LL", "CC")))
        assertEqualAfterRoundTripSerialization(myMap)
    }

    @Test
    fun `check empty map serialises as Java emptyMap`() {
        kryoSpecific("Specifically checks Kryo serialization") {
            val nameID = 0
            val serializedForm = emptyMap<Int, Int>().serialize()
            val output = ByteArrayOutputStream().apply {
                kryoMagic.writeTo(this)
                SectionId.ALT_DATA_AND_STOP.writeTo(this)
                write(DefaultClassResolver.NAME + 2)
                write(nameID)
                write(javaEmptyMapClass.name.toAscii())
                write(Kryo.NOT_NULL.toInt())
            }
            assertArrayEquals(output.toByteArray(), serializedForm.bytes)
        }
    }
}
