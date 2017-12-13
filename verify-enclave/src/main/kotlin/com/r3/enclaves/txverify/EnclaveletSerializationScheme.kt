@file:JvmName("EnclaveletSerializationScheme")
package com.r3.enclaves.txverify

import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.internal.SerializationEnvironmentImpl
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.toHexString
import net.corda.nodeapi.internal.serialization.*
import net.corda.nodeapi.internal.serialization.amqp.AbstractAMQPSerializationScheme
import net.corda.nodeapi.internal.serialization.amqp.AmqpHeaderV1_0
import net.corda.nodeapi.internal.serialization.amqp.SerializerFactory
import net.corda.nodeapi.internal.serialization.kryo.AbstractKryoSerializationScheme
import net.corda.nodeapi.internal.serialization.kryo.KryoHeaderV0_1

@Suppress("UNUSED")
private class EnclaveletSerializationScheme {
    /*
     * Registers the serialisation schemes as soon as this class is loaded into the JVM.
     */
    private companion object {
        init {
            nodeSerializationEnv = SerializationEnvironmentImpl(
                SerializationFactoryImpl().apply {
                    registerScheme(KryoVerifierSerializationScheme)
                    registerScheme(AMQPVerifierSerializationScheme)
                },
                /**
                 * Even though default context is set to Kryo P2P, the encoding will be adjusted depending on the
                 * incoming request received.
                 */
                KRYO_P2P_CONTEXT)

            /*
             * Ensure that we initialise JAXP before blacklisting is enabled.
             */
            ByteArray(0).toHexString()
        }
    }
}

private object KryoVerifierSerializationScheme : AbstractKryoSerializationScheme() {
    override fun canDeserializeVersion(byteSequence: ByteSequence, target: SerializationContext.UseCase): Boolean {
        return byteSequence == KryoHeaderV0_1 && target == SerializationContext.UseCase.P2P
    }

    override fun rpcClientKryoPool(context: SerializationContext) = throw UnsupportedOperationException()
    override fun rpcServerKryoPool(context: SerializationContext) = throw UnsupportedOperationException()
}

private object AMQPVerifierSerializationScheme : AbstractAMQPSerializationScheme(emptyList()) {
    override fun canDeserializeVersion(byteSequence: ByteSequence, target: SerializationContext.UseCase): Boolean {
        return (byteSequence == AmqpHeaderV1_0 && (target == SerializationContext.UseCase.P2P))
    }

    override fun rpcClientSerializerFactory(context: SerializationContext): SerializerFactory = throw UnsupportedOperationException()
    override fun rpcServerSerializerFactory(context: SerializationContext): SerializerFactory = throw UnsupportedOperationException()
}
