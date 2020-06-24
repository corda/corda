package net.corda.nodeapi.internal.serialization.kryo

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.serialization.internal.amqp.CORDAPP_TYPE
import net.corda.serialization.internal.amqp.PROXY_TYPE
import java.lang.reflect.Type
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure

class CustomSerializerCheckpointAdaptor<OBJ, PROXY>(private val userSerializer : SerializationCustomSerializer<OBJ, PROXY>) : Serializer<OBJ>() {

    val type: Type
    val proxyType: Type

    init {
        val types = userSerializer::class.supertypes.filter { it.jvmErasure == SerializationCustomSerializer::class }
                .flatMap { it.arguments }
                .map { it.type!!.javaType }
        if (types.size != 2) {
            throw UnableToDetermineSerializerTypesException("Unable to determine serializer parent types")
        }
        type = types[CORDAPP_TYPE]
        proxyType = types[PROXY_TYPE]
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

class UnableToDetermineSerializerTypesException : java.lang.Exception {
    constructor() : super()
    constructor(message: String?) : super(message)
    constructor(message: String?, cause: Throwable?) : super(message, cause)
    constructor(cause: Throwable?) : super(cause)
    constructor(message: String?, cause: Throwable?, enableSuppression: Boolean, writableStackTrace: Boolean) : super(message, cause, enableSuppression, writableStackTrace)
}
