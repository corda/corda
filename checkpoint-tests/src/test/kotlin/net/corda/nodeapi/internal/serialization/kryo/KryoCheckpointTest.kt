package net.corda.nodeapi.internal.serialization.kryo

import org.assertj.core.api.Assertions.assertThat
import org.junit.Ignore
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.Instant
import java.util.LinkedList
import kotlin.test.assertEquals

class KryoCheckpointTest {

    private val testSize = 1000L

    /**
     * This test just ensures that the checkpoints still work in light of [LinkedHashMapEntrySerializer].
     */
    @Test(timeout=300_000)
	fun `linked hash map can checkpoint without error`() {
        var lastKey = ""
        val dummyMap = linkedMapOf<String, Long>()
        for (i in 0..testSize) {
            dummyMap[i.toString()] = i
        }
        var it = dummyMap.iterator()
        while (it.hasNext()) {
            lastKey = it.next().key
            val bytes = KryoCheckpointSerializer.serialize(it, KRYO_CHECKPOINT_CONTEXT)
            it = KryoCheckpointSerializer.deserialize(bytes, it.javaClass, KRYO_CHECKPOINT_CONTEXT)
        }
        assertEquals(testSize.toString(), lastKey)
    }

    @Test(timeout=300_000)
    fun `empty linked hash map can checkpoint without error`() {
        val dummyMap = linkedMapOf<String, Long>()
        val it = dummyMap.iterator()
        val itKeys = dummyMap.keys.iterator()
        val itValues = dummyMap.values.iterator()
        val bytes = KryoCheckpointSerializer.serialize(it, KRYO_CHECKPOINT_CONTEXT)
        val bytesKeys = KryoCheckpointSerializer.serialize(itKeys, KRYO_CHECKPOINT_CONTEXT)
        val bytesValues = KryoCheckpointSerializer.serialize(itValues, KRYO_CHECKPOINT_CONTEXT)
        assertDoesNotThrow {
            KryoCheckpointSerializer.deserialize(bytes, it.javaClass, KRYO_CHECKPOINT_CONTEXT)
            KryoCheckpointSerializer.deserialize(bytesKeys, itKeys.javaClass, KRYO_CHECKPOINT_CONTEXT)
            KryoCheckpointSerializer.deserialize(bytesValues, itValues.javaClass, KRYO_CHECKPOINT_CONTEXT)
        }
    }

    @Test(timeout=300_000)
    fun `linked hash map with null values can checkpoint without error`() {
        val dummyMap = linkedMapOf<String?, Long?>().apply {
            put("foo", 2L)
            put(null, null)
            put("bar", 3L)
        }
        val it = dummyMap.iterator()
        val bytes = KryoCheckpointSerializer.serialize(it, KRYO_CHECKPOINT_CONTEXT)

        val itKeys = dummyMap.keys.iterator()
        itKeys.next()
        itKeys.next()
        val bytesKeys = KryoCheckpointSerializer.serialize(itKeys, KRYO_CHECKPOINT_CONTEXT)

        val itValues = dummyMap.values.iterator()
        val bytesValues = KryoCheckpointSerializer.serialize(itValues, KRYO_CHECKPOINT_CONTEXT)

        assertDoesNotThrow {
            KryoCheckpointSerializer.deserialize(bytes, it.javaClass, KRYO_CHECKPOINT_CONTEXT)
            val desItKeys = KryoCheckpointSerializer.deserialize(bytesKeys, itKeys.javaClass, KRYO_CHECKPOINT_CONTEXT)
            assertEquals("bar", desItKeys.next())
            KryoCheckpointSerializer.deserialize(bytesValues, itValues.javaClass, KRYO_CHECKPOINT_CONTEXT)
        }
    }

    @Test(timeout=300_000)
    fun `linked hash map keys can checkpoint without error`() {
        var lastKey = ""
        val dummyMap = linkedMapOf<String, Long>()
        for (i in 0..testSize) {
            dummyMap[i.toString()] = i
        }
        var it = dummyMap.keys.iterator()
        while (it.hasNext()) {
            lastKey = it.next()
            val bytes = KryoCheckpointSerializer.serialize(it, KRYO_CHECKPOINT_CONTEXT)
            it = KryoCheckpointSerializer.deserialize(bytes, it.javaClass, KRYO_CHECKPOINT_CONTEXT)
        }
        assertEquals(testSize.toString(), lastKey)
    }

    @Test(timeout=300_000)
	fun `linked hash map values can checkpoint without error`() {
        var lastValue = 0L
        val dummyMap = linkedMapOf<String, Long>()
        for (i in 0..testSize) {
            dummyMap[i.toString()] = i
        }
        var it = dummyMap.values.iterator()
        while (it.hasNext()) {
            lastValue = it.next()
            val bytes = KryoCheckpointSerializer.serialize(it, KRYO_CHECKPOINT_CONTEXT)
            it = KryoCheckpointSerializer.deserialize(bytes, it.javaClass, KRYO_CHECKPOINT_CONTEXT)
        }
        assertEquals(testSize, lastValue)
    }

    @Test(timeout = 300_000)
    fun `linked hash map values can checkpoint without error, even with repeats`() {
        var lastValue = "0"
        val dummyMap = linkedMapOf<String, String>()
        for (i in 0..testSize) {
            dummyMap[i.toString()] = (i % 10).toString()
        }
        var it = dummyMap.values.iterator()
        while (it.hasNext()) {
            lastValue = it.next()
            val bytes = KryoCheckpointSerializer.serialize(it, KRYO_CHECKPOINT_CONTEXT)
            it = KryoCheckpointSerializer.deserialize(bytes, it.javaClass, KRYO_CHECKPOINT_CONTEXT)
        }
        assertEquals((testSize % 10).toString(), lastValue)
    }

    @Ignore("Kryo optimizes boxed primitives so this does not work.  Need to customise ReferenceResolver to stop it doing it.")
    @Test(timeout = 300_000)
    fun `linked hash map values can checkpoint without error, even with repeats for boxed primitives`() {
        var lastValue = 0L
        val dummyMap = linkedMapOf<String, Long>()
        for (i in 0..testSize) {
            dummyMap[i.toString()] = (i % 10)
        }
        var it = dummyMap.values.iterator()
        while (it.hasNext()) {
            lastValue = it.next()
            val bytes = KryoCheckpointSerializer.serialize(it, KRYO_CHECKPOINT_CONTEXT)
            it = KryoCheckpointSerializer.deserialize(bytes, it.javaClass, KRYO_CHECKPOINT_CONTEXT)
        }
        assertEquals(testSize % 10, lastValue)
    }

    /**
     * This test just ensures that the checkpoints still work in light of [LinkedHashMapEntrySerializer].
     */
    @Test(timeout=300_000)
	fun `LinkedHashSet iterator can checkpoint without error`() {
        var result: Any = 0L
        val dummySet = linkedSetOf<Any>().apply { addAll(0..testSize) }
        var it = dummySet.iterator()
        while (it.hasNext()) {
            result = it.next()
            val bytes = KryoCheckpointSerializer.serialize(it, KRYO_CHECKPOINT_CONTEXT)
            it = KryoCheckpointSerializer.deserialize(bytes, it.javaClass, KRYO_CHECKPOINT_CONTEXT)
        }
        assertEquals(testSize, result)
    }

    @Test(timeout=300_000)
    fun `ArrayList iterator can checkpoint without error`() {
        testIteratorCheckpointing(ArrayList())
    }

    @Test(timeout=300_000)
    fun `HashSet iterator can checkpoint without error`() {
        testIteratorCheckpointing(HashSet())
    }

//    @Test(timeout=300_000)
//    fun `LinkedHashSet iterator can checkpoint without error`() {
//        testIteratorCheckpointing(LinkedHashSet())
//    }

    @Test(timeout=300_000)
	fun `LinkedList iterator can checkpoint without error`() {
        testIteratorCheckpointing(LinkedList())
    }

    @Test(timeout=300_000)
    fun `Instant can checkpoint without error`() {
        val original = Instant.now()
        assertThat(checkpointRoundtrip(original)).isEqualTo(original)
    }

    private fun testIteratorCheckpointing(collection: MutableCollection<Long>) {
        collection.addAll(0..testSize)
        val originalIterator = collection.iterator()
        while (true) {
            val roundtrip = checkpointRoundtrip(TestCheckpoint(collection, originalIterator))
            assertThat(roundtrip.collection).isEqualTo(collection)
            assertThat(roundtrip.iterator.hasNext()).isEqualTo(originalIterator.hasNext())
            if (!originalIterator.hasNext()) break
            assertThat(roundtrip.iterator.next()).isEqualTo(originalIterator.next())
        }
    }

    private fun <T : Any> checkpointRoundtrip(obj: T): T {
        val bytes = KryoCheckpointSerializer.serialize(obj, KRYO_CHECKPOINT_CONTEXT)
        return KryoCheckpointSerializer.deserialize(bytes, obj.javaClass, KRYO_CHECKPOINT_CONTEXT)
    }

    private data class TestCheckpoint(val collection: Collection<*>, val iterator: Iterator<*>)
}
