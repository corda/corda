package net.corda.nodeapi.internal.serialization.kryo

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output

/**
 * The [LinkedHashMap] and [LinkedHashSet] have a problem with the default Quasar/Kryo serialisation
 * in that serialising an iterator (and subsequent [LinkedHashMap.Entry]) over a sufficiently large
 * data set can lead to a stack overflow (because the object map is traversed recursively).
 *
 * We've added our own custom serializer in order to ensure that only the key/value are recorded.
 * The rest of the list isn't required at this scope.
 */
object LinkedHashMapEntrySerializer : Serializer<Map.Entry<*, *>>() {
    // Create a dummy map so that we can get the LinkedHashMap$Entry from it
    // The element type of the map doesn't matter.  The entry is all we want
    private val constructor = linkedMapOf(1L to 1).entries.first()::class.java.declaredConstructors.single().apply { isAccessible = true }

    /**
     * Kryo would end up serialising "this" entry, then serialise "this.after" recursively, leading to a very large stack.
     * we'll skip that and just write out the key/value
     */
    override fun write(kryo: Kryo, output: Output, obj: Map.Entry<*, *>) {
        val e: Map.Entry<*, *> = obj
        kryo.writeClassAndObject(output, e.key)
        kryo.writeClassAndObject(output, e.value)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out Map.Entry<*, *>>): Map.Entry<*, *> {
        val key = kryo.readClassAndObject(input)
        val value = kryo.readClassAndObject(input)
        return constructor.newInstance(0, key, value, null) as Map.Entry<*, *>
    }
}
