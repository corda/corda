package net.corda.serialization.internal

import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.internal.CheckpointSerializationContext
import net.corda.core.serialization.internal.CheckpointSerializer
import net.corda.nodeapi.internal.serialization.kryo.CordaClosureSerializer
import net.corda.testing.core.internal.CheckpointSerializationEnvironmentRule
import org.assertj.core.api.Assertions.catchThrowable
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.notNullValue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.lang.IllegalArgumentException

class KotlinLambdaCheckpointSerializationTest {

    @Rule
    @JvmField
    val testCheckpointSerialization = CheckpointSerializationEnvironmentRule()

    private lateinit var context: CheckpointSerializationContext

    private lateinit var serializer: CheckpointSerializer

    @Before
    fun setup() {
        context = CheckpointSerializationContextImpl(
                javaClass.classLoader,
                AllWhitelist,
                emptyMap(),
                true,
                null
        )

        serializer = testCheckpointSerialization.checkpointSerializer
    }

    @Test
    fun `serialization works for serializable kotlin lambdas`() {
        val value = "Hey"
        val target = @JvmSerializableLambda { value }

        val serialized = serialize(target)
        val deserialized = deserialize(serialized, target.javaClass)

        assertThat(deserialized(), `is`(value))
    }

    @Test
    @SuppressWarnings("unchecked")
    fun `serialization fails for not serializable java lambdas`() {
        val value = "Hey"
        val target = { value }

        val throwable = catchThrowable { serialize(target) }

        assertThat(throwable, notNullValue())
        assertThat(throwable, Matchers.instanceOf(IllegalArgumentException::class.java))
        assertThat(throwable.message, Matchers.containsString(CordaClosureSerializer.ERROR_MESSAGE))
    }

    private fun <T : Any> serialize(target: T): SerializedBytes<T> {
        return serializer.serialize(target, context)
    }

    private fun <T : Any> deserialize(bytes: SerializedBytes<T>, type: Class<T>): T {
        return serializer.deserialize(bytes, type, context)
    }
}
