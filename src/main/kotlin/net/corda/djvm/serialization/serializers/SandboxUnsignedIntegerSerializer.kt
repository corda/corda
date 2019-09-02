package net.corda.djvm.serialization.serializers

import net.corda.core.serialization.SerializationContext
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.djvm.serialization.deserializers.UnsignedIntegerDeserializer
import net.corda.djvm.serialization.loadClassForSandbox
import net.corda.serialization.internal.amqp.*
import org.apache.qpid.proton.amqp.UnsignedInteger
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type
import java.util.function.BiFunction
import java.util.function.Function

class SandboxUnsignedIntegerSerializer(
    classLoader: SandboxClassLoader,
    executor: BiFunction<in Any, in Any?, out Any?>
) : CustomSerializer.Is<Any>(classLoader.loadClassForSandbox(UnsignedInteger::class.java)) {
    private val transformer: Function<IntArray, out Any?>

    init {
        val transformTask = classLoader.loadClassForSandbox(UnsignedIntegerDeserializer::class.java).newInstance()
        transformer = Function { inputs ->
            executor.apply(transformTask, inputs)
        }
    }

    override val schemaForDocumentation: Schema = Schema(emptyList())

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): Any {
        return transformer.apply(intArrayOf((obj as UnsignedInteger).toInt()))!!
    }

    override fun writeDescribedObject(obj: Any, data: Data, type: Type, output: SerializationOutput, context: SerializationContext) {
        abortReadOnly()
    }
}
