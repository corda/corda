package core.serialization

import com.esotericsoftware.kryo.DefaultSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SerializationTokenTest {

    // Large tokenizable object so we can tell from the smaller number of serialized bytes it was actually tokenized
    @DefaultSerializer(SerializeAsTokenSerializer::class)
    private class LargeTokenizable(size: Int) : SerializeAsStringToken(size.toString()) {
        val bytes = OpaqueBytes(ByteArray(size))

        override fun hashCode() = bytes.bits.size

        override fun equals(other: Any?) = other is LargeTokenizable && other.bytes.bits.size == this.bytes.bits.size
    }

    @Test
    fun `write token and read tokenizable`() {
        val numBytes = 1024
        val tokenizableBefore = LargeTokenizable(numBytes)
        val serializedBytes = tokenizableBefore.serialize()
        assertThat(serializedBytes.size).isLessThan(numBytes)
        val tokenizableAfter = serializedBytes.deserialize()
        assertEquals(tokenizableBefore, tokenizableAfter)
    }

    @Test
    fun `check same sized tokenizable equal`() {
        val tokenizableBefore = LargeTokenizable(1024)
        val tokenizableAfter = LargeTokenizable(1024)
        assertEquals(tokenizableBefore, tokenizableAfter)
    }

    @Test
    fun `check different sized tokenizable not equal`() {
        val tokenizableBefore = LargeTokenizable(1024)
        val tokenizableAfter = LargeTokenizable(1025)
        assertNotEquals(tokenizableBefore, tokenizableAfter)
    }

    @DefaultSerializer(SerializeAsTokenSerializer::class)
    private class IntegerSerializeAsKeyedToken(val value: Int) : SerializeAsStringToken(value.toString())

    @Test
    fun `write and read keyed`() {
        val tokenizableBefore1 = IntegerSerializeAsKeyedToken(123)
        val tokenizableBefore2 = IntegerSerializeAsKeyedToken(456)

        val serializedBytes1 = tokenizableBefore1.serialize()
        val tokenizableAfter1 = serializedBytes1.deserialize()
        val serializedBytes2 = tokenizableBefore2.serialize()
        val tokenizableAfter2 = serializedBytes2.deserialize()

        assertThat(tokenizableAfter1).isSameAs(tokenizableBefore1)
        assertThat(tokenizableAfter2).isSameAs(tokenizableBefore2)
    }

    @DefaultSerializer(SerializeAsTokenSerializer::class)
    private class UnitSerializeAsSingletonToken : SerializeAsStringToken("Unit0")

    @Test
    fun `write and read singleton`() {
        val tokenizableBefore = UnitSerializeAsSingletonToken()
        val serializedBytes = tokenizableBefore.serialize()
        val tokenizableAfter = serializedBytes.deserialize()
        assertThat(tokenizableAfter).isSameAs(tokenizableBefore)
    }

    private class UnannotatedSerializeAsSingletonToken : SerializeAsStringToken("Unannotated0")

    @Test(expected = IllegalStateException::class)
    fun `unannotated throws`() {
        val tokenizableBefore = UnannotatedSerializeAsSingletonToken()
    }
}