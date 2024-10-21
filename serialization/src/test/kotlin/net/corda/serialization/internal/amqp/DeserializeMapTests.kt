package net.corda.serialization.internal.amqp

import net.corda.serialization.internal.amqp.testutils.TestSerializationOutput
import net.corda.serialization.internal.amqp.testutils.deserialize
import net.corda.serialization.internal.amqp.testutils.testDefaultFactoryNoEvolution
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Test
import java.io.NotSerializableException
import java.util.AbstractMap
import java.util.Dictionary
import java.util.Hashtable
import java.util.NavigableMap
import java.util.SortedMap
import java.util.TreeMap
import java.util.WeakHashMap

class DeserializeMapTests {
    companion object {
        /**
         * If you want to see the schema encoded into the envelope after serialisation change this to true
         */
        private const val VERBOSE = false
    }

    private val sf = testDefaultFactoryNoEvolution()

    @Test(timeout=300_000)
	fun mapTest() {
        data class C(val c: Map<String, Int>)

        val c = C(mapOf("A" to 1, "B" to 2))

        val serialisedBytes = TestSerializationOutput(VERBOSE, sf).serialize(c)
        DeserializationInput(sf).deserialize(serialisedBytes)
    }

    @Test(timeout=300_000)
    fun abstractMapFromMapOf() {
        data class C(val c: AbstractMap<String, Int>)

        val c = C(mapOf("A" to 1, "B" to 2) as AbstractMap)

        assertThatExceptionOfType(NotSerializableException::class.java).isThrownBy {
            TestSerializationOutput(VERBOSE, sf).serialize(c)
        }
    }

    @Test(timeout=300_000)
    fun abstractMapFromTreeMap() {
        data class C(val c: AbstractMap<String, Int>)

        val c = C(TreeMap(mapOf("A" to 1, "B" to 2)))

        assertThatExceptionOfType(NotSerializableException::class.java).isThrownBy {
            TestSerializationOutput(VERBOSE, sf).serialize(c)
        }
    }

    @Test(timeout=300_000)
	fun sortedMapTest() {
        data class C(val c: SortedMap<String, Int>)

        val c = C(sortedMapOf("A" to 1, "B" to 2))
        val serialisedBytes = TestSerializationOutput(VERBOSE, sf).serialize(c)
        DeserializationInput(sf).deserialize(serialisedBytes)
    }

    @Test(timeout=300_000)
	fun navigableMapTest() {
        data class C(val c: NavigableMap<String, Int>)

        val c = C(TreeMap(mapOf("A" to 1, "B" to 2)).descendingMap())

        val serialisedBytes = TestSerializationOutput(VERBOSE, sf).serialize(c)
        DeserializationInput(sf).deserialize(serialisedBytes)
    }

    @Test(timeout=300_000)
	fun dictionaryTest() {
        data class C(val c: Dictionary<String, Int>)

        val v: Hashtable<String, Int> = Hashtable()
        v["a"] = 1
        v["b"] = 2
        val c = C(v)

        // expected to throw
        assertThatThrownBy { TestSerializationOutput(VERBOSE, sf).serialize(c) }
                .isInstanceOf(NotSerializableException::class.java)
                .hasMessageContaining("Unable to serialise deprecated type class java.util.Dictionary.")
    }

    @Test(timeout=300_000)
	fun hashtableTest() {
        data class C(val c: Hashtable<String, Int>)

        val v: Hashtable<String, Int> = Hashtable()
        v["a"] = 1
        v["b"] = 2
        val c = C(v)

        // expected to throw
        assertThatThrownBy { TestSerializationOutput(VERBOSE, sf).serialize(c) }
                .isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("Unable to serialise deprecated type class java.util.Hashtable. Suggested fix: prefer java.util.map implementations")
    }

    @Test(timeout=300_000)
	fun hashMapTest() {
        data class C(val c: HashMap<String, Int>)

        val c = C(HashMap(mapOf("A" to 1, "B" to 2)))

        // expect this to throw
        assertThatThrownBy { TestSerializationOutput(VERBOSE, sf).serialize(c) }
                .isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("Map type class java.util.HashMap is unstable under iteration. Suggested fix: use java.util.LinkedHashMap instead.")
    }

    @Test(timeout=300_000)
	fun weakHashMapTest() {
        data class C(val c: WeakHashMap<String, Int>)

        val c = C(WeakHashMap(mapOf("A" to 1, "B" to 2)))

        assertThatThrownBy { TestSerializationOutput(VERBOSE, sf).serialize(c) }
                .isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("Weak references with map types not supported. Suggested fix: use java.util.LinkedHashMap instead.")
    }

    @Test(timeout=300_000)
	fun concreteTreeMapTest() {
        data class C(val c: TreeMap<String, Int>)

        val c = C(TreeMap(mapOf("A" to 1, "B" to 3)))

        val serialisedBytes = TestSerializationOutput(VERBOSE, sf).serialize(c)
        DeserializationInput(sf).deserialize(serialisedBytes)
    }

    @Test(timeout=300_000)
	fun concreteLinkedHashMapTest() {
        data class C(val c: LinkedHashMap<String, Int>)

        val c = C(LinkedHashMap(mapOf("A" to 1, "B" to 2)))

        val serialisedBytes = TestSerializationOutput(VERBOSE, sf).serialize(c)
        DeserializationInput(sf).deserialize(serialisedBytes)
    }
}
