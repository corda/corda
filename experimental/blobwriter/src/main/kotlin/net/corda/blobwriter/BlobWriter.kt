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

data class _i_ (val a: Int)
data class _is_ (val a: Int, val b: String)
data class _i_is__ (val a: Int, val b: _is_)
data class _Li_ (val a: List<Int>)
data class _Mis_ (val a: Map<Int, String>)

fun main (args: Array<String>) {
    initialiseSerialization()

    File("../cpp-serializer/bin/blob-inspector/test/_i_is__").writeBytes(_i_is__(1, _is_ (2, "three")).serialize().bytes)
    File("../cpp-serializer/bin/blob-inspector/test/_Li_").writeBytes(_Li_(listOf (1, 2, 3, 4, 5, 6)).serialize().bytes)
    File("../cpp-serializer/bin/blob-inspector/test/_Mis_").writeBytes(_Mis_(
            mapOf (1 to "two", 3 to "four", 5 to "six")).serialize().bytes)
}


