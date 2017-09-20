package net.corda.nodeapi.internal.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.util.DefaultClassResolver
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.serialize
import net.corda.node.services.statemachine.SessionData
import net.corda.testing.TestDependencyInjectionBase
import net.corda.testing.amqpSpecific
import org.assertj.core.api.Assertions
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.bouncycastle.asn1.x500.X500Name
import java.io.ByteArrayOutputStream
import java.io.NotSerializableException

class MapsSerializationTest : TestDependencyInjectionBase() {
    private companion object {
        val linkedMapClass = linkedMapOf<Any, Any>().javaClass
    }

    private val smallMap = mapOf("foo" to "bar", "buzz" to "bull")

    @Test
    fun `check EmptyMap serialization`() = amqpSpecific<MapsSerializationTest>("kotlin.collections.EmptyMap is not enabled for Kryo serialization") {
        assertEqualAfterRoundTripSerialization(emptyMap<Any, Any>())
    }

    @Test
    fun `check Map can be root of serialization graph`() {
        assertEqualAfterRoundTripSerialization(smallMap)
    }

    @Test
    fun `check list can be serialized as part of SessionData`() {
        val sessionData = SessionData(123, smallMap)
        assertEqualAfterRoundTripSerialization(sessionData)
    }

    @CordaSerializable
    data class WrongPayloadType(val payload: HashMap<String, String>)

    @Test
    fun `check throws for forbidden declared type`() = amqpSpecific<ListsSerializationTest>("Such exceptions are not expected in Kryo mode.") {
        val payload = HashMap<String, String>(smallMap)
        val wrongPayloadType = WrongPayloadType(payload)
        Assertions.assertThatThrownBy { wrongPayloadType.serialize() }
                .isInstanceOf(NotSerializableException::class.java).hasMessageContaining("Cannot derive map type for declaredType")
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
    fun `check empty map serialises as LinkedHashMap`() {
        val nameID = 0
        val serializedForm = emptyMap<Int, Int>().serialize()
        val output = ByteArrayOutputStream().apply {
            write(KryoHeaderV0_1.bytes)
            write(DefaultClassResolver.NAME + 2)
            write(nameID)
            write(linkedMapClass.name.toAscii())
            write(Kryo.NOT_NULL.toInt())
            write(emptyMap<Int, Int>().size)
        }
        assertArrayEquals(output.toByteArray(), serializedForm.bytes)
    }
}