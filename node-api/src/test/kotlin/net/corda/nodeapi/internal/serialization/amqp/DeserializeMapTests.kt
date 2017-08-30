package net.corda.nodeapi.internal.serialization.amqp

import org.junit.Test
import java.util.*

class DeserializeCollectionTests {
    companion object {
        /**
         * If you want to see the schema encoded into the envelope after serialisation change this to true
         */
        private const val VERBOSE = false
    }

    val sf = testDefaultFactory()

    @Test
    fun mapTest() {
        data class C(val c: Map<String, Int>)
        val c = C (mapOf("A" to 1, "B" to 2))

        val serialisedBytes = TestSerializationOutput(VERBOSE, sf).serialize(c)
        DeserializationInput(sf).deserialize(serialisedBytes)
    }

    @Test(expected=java.io.NotSerializableException::class)
    fun abstractMapFromMapOf() {
        data class C(val c: AbstractMap<String, Int>)
        val c = C (mapOf("A" to 1, "B" to 2) as AbstractMap)

        val serialisedBytes = TestSerializationOutput(VERBOSE, sf).serialize(c)
        DeserializationInput(sf).deserialize(serialisedBytes)
    }

    @Test(expected=java.io.NotSerializableException::class)
    fun abstractMapFromTreeMap() {
        data class C(val c: AbstractMap<String, Int>)
        val c = C (TreeMap(mapOf("A" to 1, "B" to 2)))

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
    fun navigableMapTest() {
        data class C(val c: NavigableMap<String, Int>)
        val c = C(TreeMap (mapOf("A" to 1, "B" to 2)).descendingMap())

        val serialisedBytes = TestSerializationOutput(VERBOSE, sf).serialize(c)
        DeserializationInput(sf).deserialize(serialisedBytes)
    }

    @Test(expected=java.io.NotSerializableException::class)
    fun dictionaryTest() {
        data class C(val c: Dictionary<String, Int>)
        val v : Hashtable<String, Int> = Hashtable()
        v.put ("a", 1)
        v.put ("b", 2)
        val c = C(v)

        // expected to throw
        TestSerializationOutput(VERBOSE, sf).serialize(c)
    }

    @Test(expected=java.lang.IllegalArgumentException::class)
    fun hashtableTest() {
        data class C(val c: Hashtable<String, Int>)
        val v : Hashtable<String, Int> = Hashtable()
        v.put ("a", 1)
        v.put ("b", 2)
        val c = C(v)

        // expected to throw
        TestSerializationOutput(VERBOSE, sf).serialize(c)
    }

    @Test(expected=java.lang.IllegalArgumentException::class)
    fun hashMapTest() {
        data class C(val c : HashMap<String, Int>)
        val c = C (HashMap (mapOf("A" to 1, "B" to 2)))

        // expect this to throw
        TestSerializationOutput(VERBOSE, sf).serialize(c)
    }

    @Test(expected=java.lang.IllegalArgumentException::class)
    fun weakHashMapTest() {
        data class C(val c : WeakHashMap<String, Int>)
        val c = C (WeakHashMap (mapOf("A" to 1, "B" to 2)))

        TestSerializationOutput(VERBOSE, sf).serialize(c)
    }

    @Test
    fun concreteTreeMapTest() {
        data class C(val c: TreeMap<String, Int>)
        val c = C(TreeMap (mapOf("A" to 1, "B" to 3)))

        val serialisedBytes = TestSerializationOutput(VERBOSE, sf).serialize(c)
        DeserializationInput(sf).deserialize(serialisedBytes)
    }

    @Test
    fun concreteLinkedHashMapTest() {
        data class C(val c : LinkedHashMap<String, Int>)
        val c = C (LinkedHashMap (mapOf("A" to 1, "B" to 2)))

        val serialisedBytes = TestSerializationOutput(VERBOSE, sf).serialize(c)
        DeserializationInput(sf).deserialize(serialisedBytes)
    }
}
