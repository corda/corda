package net.corda.nodeapi.internal.serialization.kryo

import net.corda.core.serialization.internal.checkpointDeserialize
import net.corda.core.serialization.internal.checkpointSerialize
import net.corda.testing.core.internal.CheckpointSerializationEnvironmentRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.LinkedList
import kotlin.test.assertEquals

class KryoCheckpointTest {
    companion object {
        // A value big enough to trigger any stack overflow issues
        private const val SIZE = 10_000
        private const val CHUNK = 2
    }

    @Rule
    @JvmField
    val serializationRule = CheckpointSerializationEnvironmentRule()

    @Ignore("Kryo optimizes boxed primitives so this does not work.  Need to customise ReferenceResolver to stop it doing it.")
    @Test(timeout = 300_000)
    fun `linked hash map values can checkpoint without error, even with repeats for boxed primitives`() {
        var lastValue = 0
        val dummyMap = linkedMapOf<String, Int>()
        for (i in 0..SIZE) {
            dummyMap[i.toString()] = (i % 10)
        }
        var it = dummyMap.values.iterator()
        while (it.hasNext()) {
            lastValue = it.next()
            val bytes = KryoCheckpointSerializer.serialize(it, KRYO_CHECKPOINT_CONTEXT)
            it = KryoCheckpointSerializer.deserialize(bytes, it.javaClass, KRYO_CHECKPOINT_CONTEXT)
        }
        assertEquals(SIZE % 10, lastValue)
    }

    @Test(timeout=300_000)
    fun `ArrayList iterator can checkpoint without error`() {
        testIteratorCheckpointing(ArrayList())
    }

    @Test(timeout=300_000)
    fun `LinkedList iterator can checkpoint without error`() {
        testIteratorCheckpointing(LinkedList())
    }

    @Test(timeout=300_000)
    fun `HashSet iterator can checkpoint without error`() {
        testIteratorCheckpointing(HashSet())
    }

    @Test(timeout=300_000)
    fun `LinkedHashSet iterator can checkpoint without error`() {
        testIteratorCheckpointing(LinkedHashSet())
    }

    @Test(timeout=300_000)
    fun `HashMap iterator can checkpoint without error`() {
        testMapIteratorCheckpointing(HashMap())
    }

    @Test(timeout=300_000)
    fun `LinkedHashMap iterator can checkpoint without error`() {
        testMapIteratorCheckpointing(LinkedHashMap())
    }

    @Test(timeout=300_000)
    fun `Instant can checkpoint without error`() {
        val original = Instant.now()
        assertThat(checkpointRoundtrip(original)).isEqualTo(original)
    }

    private fun testIteratorCheckpointing(collection: MutableCollection<Int>) {
        collection.addAll(0 until SIZE)
        testIteratorCheckpointing(collection.iterator())
        if (collection is List<*>) {
            testListIteratorCheckpointing(collection)
        }
    }

    private fun testIteratorCheckpointing(originalIterator: Iterator<*>) {
        var endReached = false
        for ((_, skip) in testIndices) {
            repeat(skip) {
                originalIterator.next()
            }
            val hasNext = originalIterator.hasNext()
            val roundtripIterator = checkpointRoundtrip(originalIterator)
            assertThat(hasNext).isEqualTo(originalIterator.hasNext())  // Make sure serialising it doesn't change it
            assertThat(roundtripIterator.hasNext()).isEqualTo(hasNext)
            if (!hasNext) {
                endReached = true
                break
            }
            assertThat(roundtripIterator.next()).isEqualTo(originalIterator.next())
        }
        assertThat(endReached).isTrue()
    }

    private fun testListIteratorCheckpointing(list: List<*>) {
        for ((index, _) in testIndices) {
            val originalIterator = list.listIterator(index)
            while (true) {
                val roundtripIterator = checkpointRoundtrip(originalIterator)
                assertThat(roundtripIterator.previousIndex()).isEqualTo(originalIterator.previousIndex())
                assertThat(roundtripIterator.hasPrevious()).isEqualTo(originalIterator.hasPrevious())
                if (originalIterator.hasPrevious()) {
                    assertThat(roundtripIterator.previous()).isEqualTo(originalIterator.previous())
                    roundtripIterator.next()
                    originalIterator.next()
                }
                assertThat(roundtripIterator.nextIndex()).isEqualTo(originalIterator.nextIndex())
                assertThat(roundtripIterator.hasNext()).isEqualTo(originalIterator.hasNext())
                if (!originalIterator.hasNext()) break
                assertThat(roundtripIterator.next()).isEqualTo(originalIterator.next())
            }
        }
    }

    private fun testMapIteratorCheckpointing(map: MutableMap<Int, Int>) {
        repeat(SIZE) { index ->
            map[index] = index
        }
        testIteratorCheckpointing(map.keys.iterator())
        testIteratorCheckpointing(map.values.iterator())
        testIteratorCheckpointing(map.entries.iterator())
    }

    private inline fun <reified T : Any> checkpointRoundtrip(obj: T): T {
        val bytes = obj.checkpointSerialize(KRYO_CHECKPOINT_CONTEXT)
        return bytes.checkpointDeserialize(KRYO_CHECKPOINT_CONTEXT)
    }

    /**
     * Return a Sequence of indicies which just iterates over the first and last [CHUNK], otherwise the tests take too long. The second
     * value of the [Pair] is the number of elements to skip over from the previous iteration.
     */
    private val testIndices: Sequence<Pair<Int, Int>>
        get() = generateSequence(Pair(0, 0)) { (previous, _) ->
            when {
                previous < CHUNK - 1 -> Pair(previous + 1, 0)
                previous == CHUNK - 1 -> Pair(SIZE - CHUNK, SIZE - CHUNK - previous)
                previous < SIZE - 1 -> Pair(previous + 1, 0)
                else -> null
            }
        }
}
