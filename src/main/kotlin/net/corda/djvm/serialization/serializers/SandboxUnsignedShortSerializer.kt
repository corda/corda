package net.corda.djvm.serialization.serializers

import net.corda.core.serialization.SerializationContext
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.djvm.serialization.deserializers.UnsignedShortDeserializer
import net.corda.djvm.serialization.toSandboxAnyClass
import net.corda.serialization.internal.amqp.*
import org.apache.qpid.proton.amqp.UnsignedShort
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type
import java.util.function.Function

class SandboxUnsignedShortSerializer(
    classLoader: SandboxClassLoader,
    taskFactory: Function<in Any, out Function<in Any?, out Any?>>
) : CustomSerializer.Is<Any>(classLoader.toSandboxAnyClass(UnsignedShort::class.java)) {
    @Suppress("unchecked_cast")
    private val transformer: Function<ShortArray, out Any?>
        = classLoader.createTaskFor(taskFactory, UnsignedShortDeserializer::class.java) as Function<ShortArray, out Any?>

    override val schemaForDocumentation: Schema = Schema(emptyList())

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): Any {
        return transformer.apply(shortArrayOf((obj as UnsignedShort).toShort()))!!
    }

    override fun writeDescribedObject(obj: Any, data: Data, type: Type, output: SerializationOutput, context: SerializationContext) {
        abortReadOnly()
    }
}
