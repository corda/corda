package net.corda.core.serialization.amqp

import org.junit.Test
import java.util.*

class DeserializeCollectionTests {

    companion object {
        /**
         * If you want to see the schema encoded into the envelope after serialisation change this to true
         */
        private const val VERBOSE = false
    }

    val sf = SerializerFactory()

    @Test
    fun mapTest() {
        data class C(val c: Map<String, Int>)
        val c = C (mapOf("A" to 1, "B" to 2))

        val serialisedBytes = TestSerializationOutput(VERBOSE, sf).serialize(c)
        DeserializationInput(sf).deserialize(serialisedBytes)
    }

    @Test
    fun sortedMapTest() {
        data class C(val c: SortedMap<String, Int>)
        val c = C(sortedMapOf ("A" to 1, "B" to 2))
        val serialisedBytes = TestSerializationOutput(VERBOSE, sf).serialize(c)
        DeserializationInput(sf).deserialize(serialisedBytes)
    }

    @Test
    fun treeMapTest() {
        data class C(val c: NavigableMap<String, Int>)
        val c = C(TreeMap (mapOf("A" to 1, "B" to 3)))

        val serialisedBytes = TestSerializationOutput(VERBOSE, sf).serialize(c)
        DeserializationInput(sf).deserialize(serialisedBytes)
    }

    @Test
    fun navigableMapTest() {
        data class C(val c: NavigableMap<String, Int>)
        val c = C(TreeMap (mapOf("A" to 1, "B" to 3)).descendingMap())

        val serialisedBytes = TestSerializationOutput(VERBOSE, sf).serialize(c)
        DeserializationInput(sf).deserialize(serialisedBytes)
    }

    @Test(expected=java.lang.IllegalArgumentException::class)
    fun HashMapTest() {
        data class C(val c : HashMap<String, Int>)
        val c = C (HashMap (mapOf("A" to 1, "B" to 2)))

        // expect this to throw
        TestSerializationOutput(VERBOSE, sf).serialize(c)
    }

    // unlike the above as a linked hash map is stable under iteration we should be able to
    // serialise it
    @Test
    fun linkedHashMapTest() {
        data class C(val c : LinkedHashMap<String, Int>)
        val c = C (LinkedHashMap (mapOf("A" to 1, "B" to 2)))

        val serialisedBytes = TestSerializationOutput(VERBOSE, sf).serialize(c)
        val deserializedObj = DeserializationInput(sf).deserialize(serialisedBytes)
    }




}
