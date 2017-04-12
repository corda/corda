package net.corda.core.serialization.amqp

import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*
import kotlin.collections.LinkedHashSet

class CollectionSerializer(val declaredType: ParameterizedType) : Serializer() {
    override val type: Type = declaredType as? DeserializedParameterizedType ?: DeserializedParameterizedType.make(declaredType.toString())
    private val typeName = declaredType.toString()
    override val typeDescriptor = "${hashType(type)}"

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
        return supportedTypes[clazz] ?: throw NotSerializableException("Unsupported map type $clazz.")
    }

    private val typeNotation: TypeNotation = RestrictedType(typeName, null, emptyList(), "list", Descriptor(typeDescriptor, null), emptyList())

    override fun writeClassInfo(output: SerializationOutput) {
        if (output.writeTypeNotations(typeNotation)) {
            output.requireSerializer(declaredType.actualTypeArguments[0])
        }
    }

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput) {
        // Write described
        data.putDescribed()
        data.enter()
        // Write descriptor
        data.putObject(typeNotation.descriptor.name)
        // Write list
        data.putList()
        data.enter()
        for (entry in obj as Collection<*>) {
            output.writeObjectOrNull(entry, data, declaredType.actualTypeArguments[0])
        }
        data.exit() // exit list
        data.exit() // exit described
    }

    override fun readObject(obj: Any, envelope: Envelope, input: DeserializationInput): Any {
        // TODO: Can we verify the entries in the list?
        return concreteBuilder((obj as List<*>).map { input.readObjectOrNull(it, envelope, declaredType.actualTypeArguments[0]) })
    }
}