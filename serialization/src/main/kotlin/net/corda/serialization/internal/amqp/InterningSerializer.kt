package net.corda.serialization.internal.amqp

import net.corda.core.internal.utilities.PrivateInterner
import net.corda.core.serialization.SerializationContext
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type

class InterningSerializer(private val delegate: ObjectSerializer, private val interner: PrivateInterner<Any>) : ObjectSerializer by delegate {
    companion object {
        fun maybeWrapForInterning(candidate: ObjectSerializer): ObjectSerializer {
            val clazz = candidate.type as? Class<*>
            val interner: PrivateInterner<Any>? = PrivateInterner.findFor(clazz)
            return if (interner != null) InterningSerializer(candidate, interner) else candidate
        }
    }

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput, context: SerializationContext, debugIndent: Int) {
        delegate.writeObject(obj, data, type, output, context, debugIndent)
    }

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): Any {
        return interner.intern(delegate.readObject(obj, schemas, input, context))
    }
}