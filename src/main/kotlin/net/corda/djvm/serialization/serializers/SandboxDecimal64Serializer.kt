package net.corda.djvm.serialization.serializers

import net.corda.core.serialization.SerializationContext
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.djvm.serialization.deserializers.Decimal64Deserializer
import net.corda.djvm.serialization.loadClassForSandbox
import net.corda.serialization.internal.amqp.*
import org.apache.qpid.proton.amqp.Decimal64
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type
import java.util.function.BiFunction
import java.util.function.Function

class SandboxDecimal64Serializer(
    classLoader: SandboxClassLoader,
    executor: BiFunction<in Any, in Any?, out Any?>
) : CustomSerializer.Is<Any>(classLoader.loadClassForSandbox(Decimal64::class.java)) {
    private val transformer: Function<LongArray, out Any?>

    init {
        val transformTask = classLoader.loadClassForSandbox(Decimal64Deserializer::class.java).newInstance()
        transformer = Function { inputs ->
            executor.apply(transformTask, inputs)
        }
    }

    override val schemaForDocumentation: Schema = Schema(emptyList())

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): Any {
        return transformer.apply(longArrayOf((obj as Decimal64).bits))!!
    }

    override fun writeDescribedObject(obj: Any, data: Data, type: Type, output: SerializationOutput, context: SerializationContext) {
        abortReadOnly()
    }
}
