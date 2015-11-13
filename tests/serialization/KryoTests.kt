package serialization

import com.esotericsoftware.kryo.Kryo
import core.serialization.*
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

data class Person(val name: String, val birthday: Instant?) : SerializeableWithKryo

data class MustBeWhizzy(val s: String) : SerializeableWithKryo {
    init {
        assert(s.startsWith("whiz")) { "must be whizzy" }
    }
}

class KryoTests {
    private val kryo: Kryo = kryo().apply {
        registerDataClass<Person>()
        registerDataClass<MustBeWhizzy>()
    }

    @Test
    fun ok() {
        val april_17th = Instant.parse("1984-04-17T00:30:00.00Z")
        val mike = Person("mike", april_17th)
        val bits = mike.serialize(kryo)
        assertEquals(64, bits.size)
        with(bits.deserialize<Person>(kryo)) {
            assertEquals("mike", name)
            assertEquals(april_17th, birthday)
        }
    }

    @Test
    fun nullables() {
        val bob = Person("bob", null)
        val bits = bob.serialize(kryo)
        with(bits.deserialize<Person>(kryo)) {
            assertEquals("bob", name)
            assertNull(birthday)
        }
    }

    @Test
    fun constructorInvariant() {
        val pos = MustBeWhizzy("whizzle")
        val bits = pos.serialize(kryo)
        // Hack the serialized bytes here, like a very naughty hacker might.
        bits[10] = 'o'.toByte()
        assertFailsWith<AssertionError>("must be whizzy") {
            bits.deserialize<MustBeWhizzy>(kryo)
        }
    }
}