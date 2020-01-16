package net.corda.serialization.djvm.serializers

import net.corda.core.serialization.SerializationContext
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.serialization.djvm.deserializers.Decimal64Deserializer
import net.corda.serialization.djvm.toSandboxAnyClass
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.DeserializationInput
import net.corda.serialization.internal.amqp.Schema
import net.corda.serialization.internal.amqp.SerializationOutput
import net.corda.serialization.internal.amqp.SerializationSchemas
import org.apache.qpid.proton.amqp.Decimal64
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type
import java.util.function.Function

class SandboxDecimal64Serializer(
    classLoader: SandboxClassLoader,
    taskFactory: Function<Class<out Function<*, *>>, out Function<in Any?, out Any?>>
) : CustomSerializer.Is<Any>(classLoader.toSandboxAnyClass(Decimal64::class.java)) {
    @Suppress("unchecked_cast")
    private val transformer: Function<LongArray, out Any?>
            = taskFactory.apply(Decimal64Deserializer::class.java) as Function<LongArray, out Any?>

    override val schemaForDocumentation: Schema = Schema(emptyList())

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): Any {
        return transformer.apply(longArrayOf((obj as Decimal64).bits))!!
    }

    override fun writeDescribedObject(obj: Any, data: Data, type: Type, output: SerializationOutput, context: SerializationContext) {
        abortReadOnly()
    }
}
