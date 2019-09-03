package net.corda.djvm.serialization.serializers

import net.corda.core.serialization.SerializationContext
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.djvm.serialization.deserializers.InputStreamDeserializer
import net.corda.djvm.serialization.loadClassForSandbox
import net.corda.serialization.internal.amqp.*
import org.apache.qpid.proton.codec.Data
import java.io.InputStream
import java.lang.reflect.Type
import java.util.Collections.singleton
import java.util.function.BiFunction
import java.util.function.Function

class SandboxInputStreamSerializer(
    classLoader: SandboxClassLoader,
    executor: BiFunction<in Any, in Any?, out Any?>
) : CustomSerializer.Implements<Any>(classLoader.loadClassForSandbox(InputStream::class.java)) {
    private val decoder: Function<ByteArray, out Any?>

    init {
        val decodeTask = classLoader.loadClassForSandbox(InputStreamDeserializer::class.java).newInstance()
        decoder = Function { inputs ->
            executor.apply(decodeTask, inputs)
        }
    }

    override val schemaForDocumentation: Schema = Schema(emptyList())

    override val deserializationAliases: Set<Class<*>> = singleton(InputStream::class.java)

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): Any {
        val bits = input.readObject(obj, schemas, ByteArray::class.java, context) as ByteArray
        return decoder.apply(bits)!!
    }

    override fun writeDescribedObject(obj: Any, data: Data, type: Type, output: SerializationOutput, context: SerializationContext) {
        abortReadOnly()
    }
}
