package net.corda.serialization.djvm.serializers

import net.corda.core.serialization.SerializationContext
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.serialization.djvm.deserializers.Decimal32Deserializer
import net.corda.serialization.djvm.toSandboxAnyClass
import net.corda.serialization.internal.amqp.*
import org.apache.qpid.proton.amqp.Decimal32
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type
import java.util.function.Function

class SandboxDecimal32Serializer(
    classLoader: SandboxClassLoader,
    taskFactory: Function<in Any, out Function<in Any?, out Any?>>
) : CustomSerializer.Is<Any>(classLoader.toSandboxAnyClass(Decimal32::class.java)) {
    @Suppress("unchecked_cast")
    private val transformer: Function<IntArray, out Any?>
            = classLoader.createTaskFor(taskFactory, Decimal32Deserializer::class.java) as Function<IntArray, out Any?>

    override val schemaForDocumentation: Schema = Schema(emptyList())

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): Any {
        return transformer.apply(intArrayOf((obj as Decimal32).bits))!!
    }

    override fun writeDescribedObject(obj: Any, data: Data, type: Type, output: SerializationOutput, context: SerializationContext) {
        abortReadOnly()
    }
}
