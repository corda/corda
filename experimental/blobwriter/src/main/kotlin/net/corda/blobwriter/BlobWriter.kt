@file:Suppress("ClassNaming", "MagicNumber")

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
@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
data class _Oi_ (val a: Integer)
data class _l_ (val x: Long)
data class _Ai_ (val z : Array<Int>)
data class _Ci_ (val z : IntArray)
data class _is_ (val a: Int, val b: String)
data class _i_is__ (val a: Int, val b: _is_)
data class _Li_ (val a: List<Int>)
data class _Mis_ (val a: Map<Int, String>)
data class _Mi_is__ (val a: Map<Int, _is_>)
data class _MiLs_ (val a: Map<Int, List<String>>)
data class __i_LMis_l__ (val z : _i_, val x : List<Map<Int, String>>, val y : _l_ )
data class _LAi_ (val l : List<Array<Int>>)



enum class E {
    A, B, C
}

data class _Pls_ (val a : Pair<Long, String>)

data class _e_ (val e: E)
data class _Le_ (val listy: List<E>)
data class _L_i__ (val listy: List<_i_>)

data class _ALd_ (val a: Array<List<Double>>)

fun main (args: Array<String>) {
    initialiseSerialization()
    val path = "../cpp-serializer/bin/test-files";
    File("$path/_i_").writeBytes (_i_ (69).serialize().bytes)
    File("$path/_Oi_").writeBytes (_Oi_ (Integer (1)).serialize().bytes)
    File("$path/_l_").writeBytes (_l_ (100000000000L).serialize().bytes)
    File("$path/_Li_").writeBytes (_Li_(listOf (1, 2, 3, 4, 5, 6)).serialize().bytes)
    File("$path/_Ai_").writeBytes (_Ai_(arrayOf (1, 2, 3, 4, 5, 6)).serialize().bytes)

    val v = IntArray(3)
    v[0] = 1; v[1] = 2; v[2] = 3

    File("$path/_Ci_").writeBytes (_Ci_(v).serialize().bytes)

    File("$path/_Le_").writeBytes (_Le_(listOf (E.A, E.B, E.C)).serialize().bytes)
    File("$path/_Le_2").writeBytes (_Le_(listOf (E.A, E.B, E.C, E.B, E.A)).serialize().bytes)
    File("$path/_L_i__").writeBytes(
            _L_i__(listOf (
                    _i_ (1),
                    _i_ (2),
                    _i_ (3))
            ).serialize().bytes)

    File("$path/_ALd_").writeBytes( _ALd_ (arrayOf(
            listOf (10.1, 11.2, 12.3),
            listOf (),
            listOf (13.4)
    )).serialize().bytes)


    File ("$path/_i_is__").writeBytes(_i_is__(1, _is_ (2, "three")).serialize().bytes)
    File ("$path/_Mis_").writeBytes(_Mis_(
            mapOf (1 to "two", 3 to "four", 5 to "six")).serialize().bytes)
    File ("$path/_e_").writeBytes(_e_(E.A).serialize().bytes)

    File ("$path/_Pls_").writeBytes(_Pls_(Pair (1, "two")).serialize().bytes)
    File ("$path/_Mi_is__").writeBytes(
            _Mi_is__ (mapOf (
                    1 to _is_ (2, "three"),
                    4 to _is_ (5, "six"),
                    7 to _is_ (8, "nine")
            )
    ).serialize().bytes)

    File ("$path/_MiLs_").writeBytes(
            _MiLs_ (mapOf (
                    1 to listOf ("two", "three", "four"),
                    5 to listOf ("six"),
                    7 to listOf ()
            )
    ).serialize().bytes)


    File ("$path/__i_LMis_l__").writeBytes (
            __i_LMis_l__ (
                    _i_ (666),
                    listOf (
                            mapOf (
                                    1 to "two",
                                    3 to "four",
                                    5 to "six"
                            ),
                            mapOf (
                                    7 to "eight",
                                    9 to "ten"
                            )
                    ),
                    _l_ (1000000L)
            ).serialize().bytes

    )


}


