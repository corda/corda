package net.corda.serialization.djvm.serializers

import net.corda.core.serialization.SerializationContext
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.serialization.djvm.deserializers.InputStreamDeserializer
import net.corda.serialization.djvm.toSandboxAnyClass
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.DeserializationInput
import net.corda.serialization.internal.amqp.Schema
import net.corda.serialization.internal.amqp.SerializationOutput
import net.corda.serialization.internal.amqp.SerializationSchemas
import org.apache.qpid.proton.codec.Data
import java.io.InputStream
import java.lang.reflect.Type
import java.util.function.Function

class SandboxInputStreamSerializer(
    classLoader: SandboxClassLoader,
    taskFactory: Function<Class<out Function<*, *>>, out Function<in Any?, out Any?>>
) : CustomSerializer.Implements<Any>(classLoader.toSandboxAnyClass(InputStream::class.java)) {
    @Suppress("unchecked_cast")
    private val decoder: Function<ByteArray, out Any?>
        = taskFactory.apply(InputStreamDeserializer::class.java) as Function<ByteArray, out Any?>

    override val schemaForDocumentation: Schema = Schema(emptyList())

    override val deserializationAliases = aliasFor(InputStream::class.java)

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): Any {
        val bits = input.readObject(obj, schemas, ByteArray::class.java, context) as ByteArray
        return decoder.apply(bits)!!
    }

    override fun writeDescribedObject(obj: Any, data: Data, type: Type, output: SerializationOutput, context: SerializationContext) {
        abortReadOnly()
    }
}
