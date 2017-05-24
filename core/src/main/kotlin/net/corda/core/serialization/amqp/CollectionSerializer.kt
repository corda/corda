package net.corda.core.serialization.amqp

import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*
import kotlin.collections.Collection
import kotlin.collections.LinkedHashSet
import kotlin.collections.Set

/**
 * Serialization / deserialization of predefined set of supported [Collection] types covering mostly [List]s and [Set]s.
 */
class CollectionSerializer(val declaredType: ParameterizedType, factory: SerializerFactory) : AMQPSerializer<Any> {
    override val type: Type = declaredType as? DeserializedParameterizedType ?: DeserializedParameterizedType.make(declaredType.toString())
    private val typeName = declaredType.toString()
    override val typeDescriptor = "$DESCRIPTOR_DOMAIN:${fingerprintForType(type, factory)}"

    companion object {
        private val supportedTypes: Map<Class<out Collection<*>>, (Collection<*>) -> Collection<*>> = mapOf(
                Collection::class.java to { coll -> coll },
                List::class.java to { coll -> coll },
                Set::class.java to { coll -> Collections.unmodifiableSet(LinkedHashSet(coll)) },
                SortedSet::class.java to { coll -> Collections.unmodifiableSortedSet(TreeSet(coll)) },
                NavigableSet::class.java to { coll -> Collections.unmodifiableNavigableSet(TreeSet(coll)) }
        )
    }

    private val concreteBuilder: (Collection<*>) -> Collection<*> = findConcreteType(declaredType.rawType as Class<*>)

    private fun findConcreteType(clazz: Class<*>): (Collection<*>) -> Collection<*> {
        return supportedTypes[clazz] ?: throw NotSerializableException("Unsupported collection type $clazz.")
    }

    private val typeNotation: TypeNotation = RestrictedType(typeName, null, emptyList(), "list", Descriptor(typeDescriptor, null), emptyList())

    override fun writeClassInfo(output: SerializationOutput) {
        if (output.writeTypeNotations(typeNotation)) {
            output.requireSerializer(declaredType.actualTypeArguments[0])
        }
    }

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput) {
        // Write described
        data.withDescribed(typeNotation.descriptor) {
            withList {
                for (entry in obj as Collection<*>) {
                    output.writeObjectOrNull(entry, this, declaredType.actualTypeArguments[0])
                }
            }
        }
    }

    override fun readObject(obj: Any, schema: Schema, input: DeserializationInput): Any {
        // TODO: Can we verify the entries in the list?
        return concreteBuilder((obj as List<*>).map { input.readObjectOrNull(it, schema, declaredType.actualTypeArguments[0]) })
    }
}