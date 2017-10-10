package net.corda.client.rpc.internal

import com.esotericsoftware.kryo.pool.KryoPool
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.utilities.ByteSequence
import net.corda.nodeapi.internal.serialization.*
import java.util.concurrent.atomic.AtomicBoolean

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
        val isInitialised = AtomicBoolean(false)
        fun initialiseSerialization() {
            if (!isInitialised.compareAndSet(false, true)) return
            try {
                SerializationDefaults.SERIALIZATION_FACTORY = SerializationFactoryImpl().apply {
                    registerScheme(KryoClientSerializationScheme())
                    registerScheme(AMQPClientSerializationScheme())
                }
                SerializationDefaults.P2P_CONTEXT = KRYO_P2P_CONTEXT
                SerializationDefaults.RPC_CLIENT_CONTEXT = KRYO_RPC_CLIENT_CONTEXT
            } catch (e: IllegalStateException) {
                // Check that it's registered as we expect
                val factory = SerializationDefaults.SERIALIZATION_FACTORY
                val checkedFactory = factory as? SerializationFactoryImpl
                        ?: throw IllegalStateException("RPC client encountered conflicting configuration of serialization subsystem: $factory")
                check(checkedFactory.alreadyRegisteredSchemes.any { it is KryoClientSerializationScheme }) {
                    "RPC client encountered conflicting configuration of serialization subsystem."
                }
            }
        }
    }
}