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

data class IntClass (val a: Int)
data class IntStrClass (val a: Int, val b: String)
data class IntIntStrClass (val a: Int, val b: IntStrClass)
data class IntListClass (val a: List<Int>)
data class IntStringMapClass (val a: Map<Int, String>)
enum class E {
    A, B, C
}
data class EnumClass (val e: E)
data class EnumListClass (val listy: List<E>)

fun main (args: Array<String>) {
    initialiseSerialization()

    File("../cpp-serializer/bin/blob-inspector/test/_i_is__").writeBytes(IntIntStrClass(1, IntStrClass (2, "three")).serialize().bytes)
    File("../cpp-serializer/bin/blob-inspector/test/_Li_").writeBytes(IntListClass(listOf (1, 2, 3, 4, 5, 6)).serialize().bytes)
    File("../cpp-serializer/bin/blob-inspector/test/_Mis_").writeBytes(IntStringMapClass(
            mapOf (1 to "two", 3 to "four", 5 to "six")).serialize().bytes)
    File("../cpp-serializer/bin/blob-inspector/test/_e_").writeBytes(EnumClass(E.A).serialize().bytes)
    File("../cpp-serializer/bin/blob-inspector/test/_Le_").writeBytes(EnumListClass(listOf (E.A, E.B, E.C)).serialize().bytes)
    File("../cpp-serializer/bin/blob-inspector/test/_Le_2").writeBytes(EnumListClass(listOf (E.A, E.B, E.C, E.B, E.A)).serialize().bytes)
}


