package net.corda.node.services.statemachine

import net.corda.core.node.ServiceHub
import net.corda.core.serialization.SerializationContext.UseCase
import net.corda.core.serialization.SerializationContext.UseCase.Checkpoint
import net.corda.core.serialization.SerializationContext.UseCase.P2P
import net.corda.core.serialization.SerializationDefaults.CHECKPOINT_CONTEXT
import net.corda.core.serialization.SerializationDefaults.P2P_CONTEXT
import net.corda.core.serialization.SerializationDefaults.SERIALIZATION_FACTORY
import net.corda.core.serialization.SerializationFactory.Companion.defaultFactory
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.UntrustworthyData
import net.corda.core.utilities.checkPayloadIs
import net.corda.serialization.internal.SerializeAsTokenContextImpl
import net.corda.serialization.internal.withTokenContext
import net.corda.core.serialization.deserialize as deserializeImpl
import net.corda.core.serialization.serialize as serializeImpl

inline fun <reified T : Any> StateMachineSerialization.deserialize(bytes: ByteSequence, useCase: UseCase? = null) = deserialize(bytes, T::class.java, useCase)
inline fun <reified T : Any> StateMachineSerialization.deserialize(bytes: SerializedBytes<out T>, useCase: UseCase?) = deserialize(bytes, T::class.java, useCase)
interface StateMachineSerialization {
    fun <T : Any> serialize(obj: T, useCase: UseCase? = null): SerializedBytes<T>
    fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, useCase: UseCase?): T
    fun <T : Any> checkPayloadIs(payload: SerializedBytes<Any>, type: Class<T>): UntrustworthyData<T>
}

class NodeStateMachineSerialization(serviceHub: ServiceHub, toBeTokenized: Any) : SingletonSerializeAsToken(), StateMachineSerialization {
    private val checkpointContext = CHECKPOINT_CONTEXT.withTokenContext(SerializeAsTokenContextImpl(toBeTokenized to this, SERIALIZATION_FACTORY, CHECKPOINT_CONTEXT, serviceHub))
    override fun <T : Any> serialize(obj: T, useCase: UseCase?) = when (useCase) {
        null -> defaultFactory.run { serialize(obj, defaultContext) }
        Checkpoint -> defaultFactory.serialize(obj, checkpointContext)
        P2P -> defaultFactory.serialize(obj, P2P_CONTEXT)
        else -> throw UnsupportedOperationException(useCase.toString())
    }

    override fun <T : Any> deserialize(byteSequence: ByteSequence, clazz: Class<T>, useCase: UseCase?) = when (useCase) {
        null -> defaultFactory.run { deserialize(byteSequence, clazz, defaultContext) }
        Checkpoint -> defaultFactory.deserialize(byteSequence, clazz, checkpointContext)
        else -> throw UnsupportedOperationException(useCase.toString())
    }

    override fun <T : Any> checkPayloadIs(payload: SerializedBytes<Any>, type: Class<T>) = payload.checkPayloadIs(type)
}

class NodeStateMachineSerializationImplementation(private val serviceHub: ServiceHub) : (Any) -> NodeStateMachineSerialization {
    override fun invoke(toBeTokenized: Any) = NodeStateMachineSerialization(serviceHub, toBeTokenized)
}
