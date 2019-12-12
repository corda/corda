package net.corda.serialization.djvm.serializers

import net.corda.core.serialization.SerializationContext
import net.corda.serialization.internal.amqp.AMQPSerializer
import net.corda.serialization.internal.amqp.DeserializationInput
import net.corda.serialization.internal.amqp.SerializationOutput
import net.corda.serialization.internal.amqp.SerializationSchemas
import net.corda.serialization.internal.amqp.typeDescriptorFor
import org.apache.qpid.proton.amqp.Binary
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type
import java.util.function.Function

class PrimitiveSerializer(
    override val type: Class<*>,
    private val sandboxBasicInput: Function<in Any?, out Any?>
) : AMQPSerializer<Any> {
    override val typeDescriptor: Symbol = typeDescriptorFor(type)

    override fun readObject(
        obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext
    ): Any {
        return (obj as? Binary)?.array ?: sandboxBasicInput.apply(obj)!!
    }

    override fun writeClassInfo(output: SerializationOutput) {
        abortReadOnly()
    }

    override fun writeObject(
        obj: Any, data: Data, type: Type, output: SerializationOutput, context: SerializationContext, debugIndent: Int
    ) {
        abortReadOnly()
    }
}
