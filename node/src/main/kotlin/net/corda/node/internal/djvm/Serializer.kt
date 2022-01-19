package net.corda.node.internal.djvm

import net.corda.core.internal.SerializedStateAndRef
import net.corda.core.serialization.AMQP_ENVELOPE_CACHE_INITIAL_CAPACITY
import net.corda.core.serialization.AMQP_ENVELOPE_CACHE_PROPERTY
import net.corda.core.serialization.DESERIALIZATION_CACHE_PROPERTY
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.serialize
import net.corda.core.utilities.ByteSequence
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.node.djvm.ComponentBuilder
import net.corda.serialization.djvm.createSandboxSerializationEnv
import java.util.function.Function

class Serializer(
    private val classLoader: SandboxClassLoader,
    customSerializerNames: Set<String>,
    serializationWhitelists: Set<String>
) {
    private val factory: SerializationFactory
    private val context: SerializationContext

    init {
        val env = createSandboxSerializationEnv(classLoader, customSerializerNames, serializationWhitelists)
        factory = env.serializationFactory
        context = env.p2pContext.withProperties(mapOf<Any, Any>(
            // Duplicate the P2P SerializationContext and give it
            // these extra properties, just for this transaction.
            AMQP_ENVELOPE_CACHE_PROPERTY to HashMap<Any, Any>(AMQP_ENVELOPE_CACHE_INITIAL_CAPACITY),
            DESERIALIZATION_CACHE_PROPERTY to HashMap<Any, Any>()
        ))
    }

    /**
     * Convert a list of [SerializedStateAndRef] objects into arrays
     * of deserialized sandbox objects. We will pass this array into
     * [LtxSupplierFactory][net.corda.node.djvm.LtxSupplierFactory]
     * to be transformed finally to a list of
     * [StateAndRef][net.corda.core.contracts.StateAndRef] objects,
     */
    fun deserialize(stateRefs: List<SerializedStateAndRef>): Array<Array<out Any?>> {
        return stateRefs.map {
            arrayOf(deserialize(it.serializedState), deserialize(it.ref.serialize()))
        }.toTypedArray()
    }

    /**
     * Generate a [Function] that deserializes a [ByteArray] into an instance
     * of the given sandbox class. We import this [Function] into the sandbox
     * so that [ComponentBuilder] can deserialize objects lazily.
     */
    fun deserializerFor(clazz: Class<*>): Function<ByteArray?, out Any?> {
        val sandboxClass = classLoader.toSandboxClass(clazz)
        return Function { bytes ->
            bytes?.run {
                factory.deserialize(ByteSequence.of(this), sandboxClass, context)
            }
        }
    }

    fun deserializeTo(clazz: Class<*>, bytes: ByteSequence): Any {
        val sandboxClass = classLoader.toSandboxClass(clazz)
        return factory.deserialize(bytes, sandboxClass, context)
    }

    inline fun <reified T : Any> deserialize(bytes: SerializedBytes<T>?): Any? {
        return deserializeTo(T::class.java, bytes ?: return null)
    }
}
