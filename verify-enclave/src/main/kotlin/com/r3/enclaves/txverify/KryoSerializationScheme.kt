package com.r3.enclaves.txverify

import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.utilities.ByteSequence
import net.corda.nodeapi.internal.serialization.AbstractKryoSerializationScheme
import net.corda.nodeapi.internal.serialization.KRYO_P2P_CONTEXT
import net.corda.nodeapi.internal.serialization.KryoHeaderV0_1
import net.corda.nodeapi.internal.serialization.SerializationFactoryImpl

@Suppress("UNUSED")
private class KryoVerifierSerializationScheme : AbstractKryoSerializationScheme() {
    override fun canDeserializeVersion(byteSequence: ByteSequence, target: SerializationContext.UseCase): Boolean {
        return byteSequence == KryoHeaderV0_1 && target == SerializationContext.UseCase.P2P
    }

    override fun rpcClientKryoPool(context: SerializationContext) = throw UnsupportedOperationException()
    override fun rpcServerKryoPool(context: SerializationContext) = throw UnsupportedOperationException()

    private companion object {
        init {
            SerializationDefaults.SERIALIZATION_FACTORY = SerializationFactoryImpl().apply {
                registerScheme(KryoVerifierSerializationScheme())
            }
            SerializationDefaults.P2P_CONTEXT = KRYO_P2P_CONTEXT
        }
    }
}
