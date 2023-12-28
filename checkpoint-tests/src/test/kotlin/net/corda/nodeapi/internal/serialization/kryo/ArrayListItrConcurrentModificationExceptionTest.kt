package net.corda.nodeapi.internal.serialization.kryo

import net.corda.core.serialization.EncodingWhitelist
import net.corda.core.serialization.internal.CheckpointSerializationContext
import net.corda.core.serialization.internal.checkpointDeserialize
import net.corda.core.serialization.internal.checkpointSerialize
import net.corda.coretesting.internal.rigorousMock
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.CheckpointSerializationContextImpl
import net.corda.serialization.internal.CordaSerializationEncoding
import net.corda.testing.core.internal.CheckpointSerializationEnvironmentRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever

@RunWith(Parameterized::class)
class ArrayListItrConcurrentModificationExceptionTest(private val compression: CordaSerializationEncoding?) {
    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun compression() = arrayOf<CordaSerializationEncoding?>(null) + CordaSerializationEncoding.values()
    }

    @get:Rule
    val serializationRule = CheckpointSerializationEnvironmentRule(inheritable = true)
    private lateinit var context: CheckpointSerializationContext

    @Before
    fun setup() {
        context = CheckpointSerializationContextImpl(
                deserializationClassLoader = javaClass.classLoader,
                whitelist = AllWhitelist,
                properties = emptyMap(),
                objectReferencesEnabled = true,
                encoding = compression,
                encodingWhitelist = rigorousMock<EncodingWhitelist>().also {
                    if (compression != null) doReturn(true).whenever(it).acceptEncoding(compression)
                })
    }

    @Test(timeout=300_000)
    fun `LinkedHashSet iterator can checkpoint without error`() {
        runTestWithCollection(LinkedHashSet())
    }

    @Test(timeout=300_000)
    fun `HashMap iterator can checkpoint without error`() {
        runTestWithCollection(HashMap())
    }

    @Test(timeout=300_000)
    fun `LinkedHashMap iterator can checkpoint without error`() {
        runTestWithCollection(LinkedHashMap())
    }


    private data class TestCheckpoint<C,I>(val list: C, val iterator: I)

    private fun runTestWithCollection(collection: MutableCollection<Int>) {

        for (i in 1..100) {
            collection.add(i)
        }

        val iterator = collection.iterator()
        iterator.next()

        val checkpoint = TestCheckpoint(collection, iterator)

        val serializedBytes = checkpoint.checkpointSerialize(context)
        val deserializedCheckpoint = serializedBytes.checkpointDeserialize(context)

        assertThat(deserializedCheckpoint.list).isEqualTo(collection)
        assertThat(deserializedCheckpoint.iterator.next()).isEqualTo(2)
        assertThat(deserializedCheckpoint.iterator.hasNext()).isTrue()
    }

    private fun runTestWithCollection(collection: MutableMap<Int, Int>) {

        for (i in 1..100) {
            collection[i] = i
        }

        val iterator = collection.iterator()
        iterator.next()

        val checkpoint = TestCheckpoint(collection, iterator)

        val serializedBytes = checkpoint.checkpointSerialize(context)
        val deserializedCheckpoint = serializedBytes.checkpointDeserialize(context)

        assertThat(deserializedCheckpoint.list).isEqualTo(collection)
        assertThat(deserializedCheckpoint.iterator.next().key).isEqualTo(2)
        assertThat(deserializedCheckpoint.iterator.hasNext()).isTrue()
    }
}
