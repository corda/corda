package net.corda.nodeapi.internal.serialization.kryo

import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
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
                put(null, null)
        }
        val it = dummyMap.iterator()
        val bytes = KryoCheckpointSerializer.serialize(it, KRYO_CHECKPOINT_CONTEXT)

        val itKeys = dummyMap.keys.iterator()
        val bytesKeys = KryoCheckpointSerializer.serialize(itKeys, KRYO_CHECKPOINT_CONTEXT)

        val itValues = dummyMap.values.iterator()
        val bytesValues = KryoCheckpointSerializer.serialize(itValues, KRYO_CHECKPOINT_CONTEXT)

        assertDoesNotThrow {
            KryoCheckpointSerializer.deserialize(bytes, it.javaClass, KRYO_CHECKPOINT_CONTEXT)
            KryoCheckpointSerializer.deserialize(bytesKeys, itKeys.javaClass, KRYO_CHECKPOINT_CONTEXT)
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

    /**
     * This test just ensures that the checkpoints still work in light of [LinkedHashMapEntrySerializer].
     */
    @Test(timeout=300_000)
	fun `linked hash set can checkpoint without error`() {
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

    /**
     * This test just ensures that the checkpoints still work in light of [LinkedListItrSerializer].
     */
    @Test(timeout=300_000)
	fun `linked list can checkpoint without error`() {
        var result: Any = 0L
        val dummyList = LinkedList<Long>().apply { addAll(0..testSize) }

        var it = dummyList.iterator()
        while (it.hasNext()) {
            result = it.next()
            val bytes = KryoCheckpointSerializer.serialize(it, KRYO_CHECKPOINT_CONTEXT)
            it = KryoCheckpointSerializer.deserialize(bytes, it.javaClass, KRYO_CHECKPOINT_CONTEXT)
        }
        assertEquals(testSize, result)
    }
}
