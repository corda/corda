package net.corda.serialization.djvm.serializers

import net.corda.core.serialization.SerializationContext
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.serialization.djvm.deserializers.CreateCurrency
import net.corda.serialization.djvm.toSandboxAnyClass
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.DeserializationInput
import net.corda.serialization.internal.amqp.Schema
import net.corda.serialization.internal.amqp.SerializationOutput
import net.corda.serialization.internal.amqp.SerializationSchemas
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type
import java.util.Currency
import java.util.function.Function

class SandboxCurrencySerializer(
    classLoader: SandboxClassLoader,
    taskFactory: Function<Class<out Function<*, *>>, out Function<in Any?, out Any?>>,
    basicInput: Function<in Any?, out Any?>
) : CustomSerializer.Is<Any>(classLoader.toSandboxAnyClass(Currency::class.java)) {
    private val creator: Function<Any?, Any?>

    init {
        val createTask = taskFactory.apply(CreateCurrency::class.java)
        creator = basicInput.andThen(createTask)
    }

    override val deserializationAliases = aliasFor(Currency::class.java)

    override val schemaForDocumentation: Schema = Schema(emptyList())

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): Any {
        return creator.apply(obj)!!
    }

    override fun writeDescribedObject(obj: Any, data: Data, type: Type, output: SerializationOutput, context: SerializationContext) {
        abortReadOnly()
    }
}
