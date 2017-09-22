package net.corda.client.rpc.internal

import com.esotericsoftware.kryo.pool.KryoPool
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.utilities.ByteSequence
import net.corda.nodeapi.internal.serialization.*

class KryoClientSerializationScheme : AbstractKryoSerializationScheme() {
    override fun canDeserializeVersion(byteSequence: ByteSequence, target: SerializationContext.UseCase): Boolean {
        return byteSequence == KryoHeaderV0_1 && (target == SerializationContext.UseCase.RPCClient || target == SerializationContext.UseCase.P2P)
    }

    override fun rpcClientKryoPool(context: SerializationContext): KryoPool {
        return KryoPool.Builder {
            DefaultKryoCustomizer.customize(RPCKryo(RpcClientObservableSerializer, context)).apply {
                classLoader = context.deserializationClassLoader
            }
        }.build()
    }

    // We're on the client and don't have access to server classes.
    override fun rpcServerKryoPool(context: SerializationContext): KryoPool = throw UnsupportedOperationException()

    companion object {
        fun initialiseSerialization() {
            try {
                SerializationDefaults.SERIALIZATION_FACTORY = SerializationFactoryImpl().apply {
                    registerScheme(KryoClientSerializationScheme())
                    registerScheme(AMQPClientSerializationScheme())
                }
                SerializationDefaults.P2P_CONTEXT = KRYO_P2P_CONTEXT
                SerializationDefaults.RPC_CLIENT_CONTEXT = KRYO_RPC_CLIENT_CONTEXT
            } catch(e: IllegalStateException) {
                // Check that it's registered as we expect
                check(SerializationDefaults.SERIALIZATION_FACTORY is SerializationFactoryImpl) { "RPC client encountered conflicting configuration of serialization subsystem." }
                check((SerializationDefaults.SERIALIZATION_FACTORY as SerializationFactoryImpl).alreadyRegisteredSchemes.any { it is KryoClientSerializationScheme }) { "RPC client encountered conflicting configuration of serialization subsystem." }
            }
        }
    }
}