package net.corda.djvm.serialization.serializers

import net.corda.core.serialization.SerializationContext
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.djvm.serialization.deserializers.UnsignedShortDeserializer
import net.corda.djvm.serialization.loadClassForSandbox
import net.corda.serialization.internal.amqp.*
import org.apache.qpid.proton.amqp.UnsignedShort
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type
import java.util.function.BiFunction
import java.util.function.Function

class SandboxUnsignedShortSerializer(
    classLoader: SandboxClassLoader,
    executor: BiFunction<in Any, in Any?, out Any?>
) : CustomSerializer.Is<Any>(classLoader.loadClassForSandbox(UnsignedShort::class.java)) {
    private val transformer: Function<ShortArray, out Any?>

    init {
        val transformTask = classLoader.loadClassForSandbox(UnsignedShortDeserializer::class.java).newInstance()
        transformer = Function { inputs ->
            executor.apply(transformTask, inputs)
        }
    }

    override val schemaForDocumentation: Schema = Schema(emptyList())

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): Any {
        return transformer.apply(shortArrayOf((obj as UnsignedShort).toShort()))!!
    }

    override fun writeDescribedObject(obj: Any, data: Data, type: Type, output: SerializationOutput, context: SerializationContext) {
        abortReadOnly()
    }
}
