package net.corda.djvm.serialization.serializers

import net.corda.core.serialization.SerializationContext
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.djvm.serialization.deserializers.SymbolDeserializer
import net.corda.djvm.serialization.toSandboxAnyClass
import net.corda.serialization.internal.amqp.*
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type
import java.util.function.Function

class SandboxSymbolSerializer(
    classLoader: SandboxClassLoader,
    taskFactory: Function<in Any, out Function<in Any?, out Any?>>,
    basicInput: Function<in Any?, out Any?>
) : CustomSerializer.Is<Any>(classLoader.toSandboxAnyClass(Symbol::class.java)) {
    private val transformer: Function<String, out Any?>

    init {
        val transformTask = classLoader.createTaskFor(taskFactory, SymbolDeserializer::class.java)
        @Suppress("unchecked_cast")
        transformer = basicInput.andThen(transformTask) as Function<String, Any?>
    }

    override val schemaForDocumentation: Schema = Schema(emptyList())

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): Any {
        return transformer.apply((obj as Symbol).toString())!!
    }

    override fun writeDescribedObject(obj: Any, data: Data, type: Type, output: SerializationOutput, context: SerializationContext) {
        abortReadOnly()
    }
}
