package net.corda.serialization.internal.amqp

import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.uncheckedCast
import net.corda.core.internal.utilities.Internable
import net.corda.core.internal.utilities.PrivateInterner
import net.corda.core.serialization.SerializationContext
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type
import kotlin.reflect.full.companionObjectInstance

class InterningSerializer(private val delegate: ObjectSerializer, private val interner: PrivateInterner<Any>) : ObjectSerializer by delegate {
    companion object {
        @VisibleForTesting
        fun findInterner(clazz: Class<*>): PrivateInterner<Any>? {
            return clazz.kotlin.companionObjectInstance?.let {
                (it as? Internable<*>)?.let {
                    uncheckedCast(it.interner)
                }
            }
        }

        fun maybeWrapForInterning(candidate: ObjectSerializer): ObjectSerializer {
            val clazz = candidate.type as? Class<*>
            val interner: PrivateInterner<Any>? = if (clazz != null) findInterner(clazz) ?: findInterner(clazz.superclass) else null
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