package net.corda.nodeapi.internal.serialization.kryo

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.core.serialization.CheckpointCustomSerializer
import net.corda.serialization.internal.amqp.CORDAPP_TYPE
import java.lang.reflect.Type
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure

class CustomSerializerCheckpointAdaptor<OBJ, PROXY>(private val userSerializer : CheckpointCustomSerializer<OBJ, PROXY>) : Serializer<OBJ>() {

    val cordappType: Type

    init {
        val types: List<Type> = userSerializer::class
                .supertypes
                .filter { it.jvmErasure == CheckpointCustomSerializer::class }
                .flatMap { it.arguments }
                .mapNotNull { it.type?.javaType }

        // We are expecting a cordapp type and a proxy type.
        // We will only use the cordapp type in this class
        // but we want to check both are present.
        val typeParameterCount = 2
        if (types.size != typeParameterCount) {
            throw UnableToDetermineSerializerTypesException("Unable to determine serializer parent types")
        }
        cordappType = types[CORDAPP_TYPE]
    }

    override fun write(kryo: Kryo, output: Output, obj: OBJ) {
        val proxy = userSerializer.toProxy(obj)
        kryo.writeClassAndObject(output, proxy)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<OBJ>): OBJ {
        @Suppress("UNCHECKED_CAST")
        val proxy = kryo.readClassAndObject(input) as PROXY
        return userSerializer.fromProxy(proxy)
    }
}

class UnableToDetermineSerializerTypesException(message: String) : java.lang.Exception(message)
