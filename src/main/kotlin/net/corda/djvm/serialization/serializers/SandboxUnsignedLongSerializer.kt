package net.corda.djvm.serialization.serializers

import net.corda.core.serialization.SerializationContext
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.djvm.serialization.deserializers.UnsignedLongDeserializer
import net.corda.djvm.serialization.loadClassForSandbox
import net.corda.serialization.internal.amqp.*
import org.apache.qpid.proton.amqp.UnsignedLong
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type
import java.util.Collections.singleton
import java.util.function.BiFunction
import java.util.function.Function

class SandboxUnsignedLongSerializer(
    classLoader: SandboxClassLoader,
    executor: BiFunction<in Any, in Any?, out Any?>
) : CustomSerializer.Is<Any>(classLoader.loadClassForSandbox(UnsignedLong::class.java)) {
    private val transformer: Function<LongArray, out Any?>

    init {
        val transformTask = classLoader.loadClassForSandbox(UnsignedLongDeserializer::class.java).newInstance()
        transformer = Function { inputs ->
            executor.apply(transformTask, inputs)
        }
    }

    override val schemaForDocumentation: Schema = Schema(emptyList())

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): Any {
        return transformer.apply(longArrayOf((obj as UnsignedLong).toLong()))!!
    }

    override fun writeDescribedObject(obj: Any, data: Data, type: Type, output: SerializationOutput, context: SerializationContext) {
        abortReadOnly()
    }
}