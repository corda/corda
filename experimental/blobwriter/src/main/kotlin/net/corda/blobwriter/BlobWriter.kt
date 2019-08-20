package net.corda.blobwriter

import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.serialization.internal.*
import net.corda.serialization.internal.amqp.AbstractAMQPSerializationScheme
import net.corda.serialization.internal.amqp.amqpMagic
import java.io.File

object AMQPInspectorSerializationScheme : AbstractAMQPSerializationScheme(emptyList()) {
    override fun canDeserializeVersion (
            magic: CordaSerializationMagic,
            target: SerializationContext.UseCase
    ): Boolean {
        return magic == amqpMagic
    }

    override fun rpcClientSerializerFactory(context: SerializationContext) = throw UnsupportedOperationException()
    override fun rpcServerSerializerFactory(context: SerializationContext) = throw UnsupportedOperationException()
}

val BLOB_WRITER_CONTEXT = SerializationContextImpl(
        amqpMagic,
        SerializationDefaults.javaClass.classLoader,
        AllWhitelist,
        emptyMap(),
        true,
        SerializationContext.UseCase.P2P,
        null
)

fun initialiseSerialization() {
    _contextSerializationEnv.set (
            SerializationEnvironment.with (
                    SerializationFactoryImpl().apply {
                        registerScheme (AMQPInspectorSerializationScheme)
                    },
                    p2pContext = BLOB_WRITER_CONTEXT
            )
    )
}

data class Test (val a: Int, val b: Int)

fun main (args: Array<String>) {
    initialiseSerialization()

    println ("HELLO WORLD!")

    File("./test").writeBytes(Test(1, 2).serialize().bytes)
}


