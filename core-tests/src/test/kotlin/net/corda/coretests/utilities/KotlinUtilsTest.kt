package net.corda.coretests.utilities

import com.esotericsoftware.kryo.KryoException
import net.corda.core.crypto.random63BitValue
import net.corda.core.serialization.ClassWhitelist
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.internal.checkpointDeserialize
import net.corda.core.serialization.internal.checkpointSerialize
import net.corda.core.utilities.transient
import net.corda.nodeapi.internal.serialization.kryo.KRYO_CHECKPOINT_CONTEXT
import net.corda.serialization.internal.CheckpointSerializationContextImpl
import net.corda.testing.core.SerializationEnvironmentRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertTrue

object EmptyWhitelist : ClassWhitelist {
    override fun hasListed(type: Class<*>): Boolean = false
}

class KotlinUtilsTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val KRYO_CHECKPOINT_NOWHITELIST_CONTEXT = CheckpointSerializationContextImpl(
            javaClass.classLoader,
            EmptyWhitelist,
            emptyMap(),
            true,
            null)

    @Test(timeout=300_000)
	fun `transient property which is null`() {
        val test = NullTransientProperty()
        test.transientValue
        test.transientValue
        assertThat(test.evalCount).isEqualTo(1)
    }

    @Test(timeout=300_000)
	fun `checkpointing a transient property with non-capturing lambda`() {
        val original = NonCapturingTransientProperty()
        val originalVal = original.transientVal
        val copy = original.checkpointSerialize(context = KRYO_CHECKPOINT_CONTEXT).checkpointDeserialize(context = KRYO_CHECKPOINT_CONTEXT)
        val copyVal = copy.transientVal
        assertThat(copyVal).isNotEqualTo(originalVal)
        assertThat(copy.transientVal).isEqualTo(copyVal)
    }

    @Test(timeout=300_000)
	fun `deserialise transient property with non-capturing lambda`() {
        val anException = assertThrows<KryoException> {
            val original = NonCapturingTransientProperty()
            original.checkpointSerialize(context = KRYO_CHECKPOINT_CONTEXT).checkpointDeserialize(context = KRYO_CHECKPOINT_NOWHITELIST_CONTEXT)
        }
        anException.message?.let { assertTrue(it.contains("is not annotated or on the whitelist, so cannot be used in serialization")) }
    }

    @Test(timeout=300_000)
	fun `checkpointing a transient property with capturing lambda`() {
        val original = CapturingTransientProperty("Hello")
        val originalVal = original.transientVal
        val copy = original.checkpointSerialize(context = KRYO_CHECKPOINT_CONTEXT).checkpointDeserialize(context = KRYO_CHECKPOINT_CONTEXT)
        val copyVal = copy.transientVal
        assertThat(copyVal).isNotEqualTo(originalVal)
        assertThat(copy.transientVal).isEqualTo(copyVal)
        assertThat(copy.transientVal).startsWith("Hello")
    }

    @Test(timeout=300_000)
	fun `deserialise transient property with capturing lambda`() {
        val anException = assertThrows<KryoException> {
            val original = CapturingTransientProperty("Hello")
            original.checkpointSerialize(context = KRYO_CHECKPOINT_CONTEXT).checkpointDeserialize(context = KRYO_CHECKPOINT_NOWHITELIST_CONTEXT)
        }
        anException.message?.let { assertTrue(it.contains("is not annotated or on the whitelist, so cannot be used in serialization")) }
    }

    private class NullTransientProperty {
        var evalCount = 0
        val transientValue by transient {
            evalCount++
            null
        }
    }

    @CordaSerializable
    private class NonCapturingTransientProperty {
        val transientVal by transient { random63BitValue() }
    }

    @CordaSerializable
    private class CapturingTransientProperty(val prefix: String, val seed: Long = random63BitValue()) {
        val transientVal by transient { prefix + seed + random63BitValue() }
    }
}
