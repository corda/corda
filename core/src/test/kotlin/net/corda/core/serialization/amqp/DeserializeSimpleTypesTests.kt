package net.corda.core.serialization.amqp

import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals

/**
 * Prior to certain fixes being made within the [PropertySerializaer] classes these simple
 * deserialization operations would've blown up with type mismatch errors where the deserlized
 * char property of the class would've been treated as an Integer and given to the constructor
 * as such
 */
class DeserializeSimpleTypesTests {
    @Test
    fun testChar() {
        data class C(val c: Char)
        val c = C('c')
        val serialisedC = SerializationOutput().serialize(c)
        val deserializedC = DeserializationInput().deserialize(serialisedC)

        assertEquals(c.c, deserializedC.c)
    }

    @Test
    fun testCharacter() {
        data class C(val c: Character)
        val c = C(Character ('c'))
        val serialisedC = SerializationOutput().serialize(c)
        val deserializedC = DeserializationInput().deserialize(serialisedC)

        assertEquals(c.c, deserializedC.c)
    }
}

