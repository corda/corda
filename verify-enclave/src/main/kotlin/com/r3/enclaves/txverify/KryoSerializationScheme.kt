package com.r3.enclaves.txverify

import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationFactory
import net.corda.core.utilities.ByteSequence
import net.corda.nodeapi.internal.serialization.AbstractKryoSerializationScheme
import net.corda.nodeapi.internal.serialization.KryoHeaderV0_1

@Suppress("UNUSED")
private class KryoVerifierSerializationScheme(serializationFactory: SerializationFactory) : AbstractKryoSerializationScheme(serializationFactory) {
    override fun canDeserializeVersion(byteSequence: ByteSequence, target: SerializationContext.UseCase): Boolean {
        return byteSequence == KryoHeaderV0_1 && target == SerializationContext.UseCase.P2P
    }

    override fun rpcClientKryoPool(context: SerializationContext) = throw UnsupportedOperationException()
    override fun rpcServerKryoPool(context: SerializationContext) = throw UnsupportedOperationException()
}
