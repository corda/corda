package net.corda.nodeapi.internal.serialization.amqp

import org.junit.Test
import java.util.*
import org.apache.qpid.proton.codec.Data

class DeserializeCollectionTests {

    class TestSerializationOutput(
            private val verbose: Boolean,
            serializerFactory: SerializerFactory = SerializerFactory()) : SerializationOutput(serializerFactory) {

        override fun writeSchema(schema: Schema, data: Data) {
            if (verbose) println(schema)
            super.writeSchema(schema, data)
        }
    }
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
    fun abstractMapFromMapOf() {
        data class C(val c: AbstractMap<String, Int>)
        val c = C (mapOf("A" to 1, "B" to 2) as AbstractMap)

        val serialisedBytes = TestSerializationOutput(VERBOSE, sf).serialize(c)
        DeserializationInput(sf).deserialize(serialisedBytes)
    }

    @Test
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

    @Test
    fun dictionaryTest() {
        data class C(val c: Dictionary<String, Int>)
        var v : Hashtable<String, Int> = Hashtable()
        v.put ("a", 1)
        v.put ("b", 2)
        val c = C(v)
        val serialisedBytes = TestSerializationOutput(VERBOSE, sf).serialize(c)
        DeserializationInput(sf).deserialize(serialisedBytes)
    }

    @Test(expected=java.lang.IllegalArgumentException::class)
    fun hashMapTest() {
        data class C(val c : HashMap<String, Int>)
        val c = C (HashMap (mapOf("A" to 1, "B" to 2)))

        // expect this to throw
        TestSerializationOutput(VERBOSE, sf).serialize(c)
    }

    @Test
    fun weakHashMapTest() {
        data class C(val c : WeakHashMap<String, Int>)
        val c = C (WeakHashMap (mapOf("A" to 1, "B" to 2)))

        val serialisedBytes = TestSerializationOutput(VERBOSE, sf).serialize(c)
        DeserializationInput(sf).deserialize(serialisedBytes)
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
