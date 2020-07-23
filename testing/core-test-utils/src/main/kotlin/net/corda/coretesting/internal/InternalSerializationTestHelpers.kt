package net.corda.coretesting.internal

import net.corda.nodeapi.internal.rpc.client.AMQPClientSerializationScheme
import net.corda.core.internal.createInstancesOfClassesImplementing
import net.corda.core.serialization.CheckpointCustomSerializer
import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.nodeapi.internal.serialization.amqp.AMQPServerSerializationScheme
import net.corda.nodeapi.internal.serialization.kryo.KRYO_CHECKPOINT_CONTEXT
import net.corda.nodeapi.internal.serialization.kryo.KryoCheckpointSerializer
import net.corda.serialization.internal.AMQP_P2P_CONTEXT
import net.corda.serialization.internal.AMQP_RPC_CLIENT_CONTEXT
import net.corda.serialization.internal.AMQP_RPC_SERVER_CONTEXT
import net.corda.serialization.internal.AMQP_STORAGE_CONTEXT
import net.corda.serialization.internal.SerializationFactoryImpl
import net.corda.testing.common.internal.asContextEnv
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

val inVMExecutors = ConcurrentHashMap<SerializationEnvironment, ExecutorService>()

fun createTestSerializationEnv(): SerializationEnvironment {
    return createTestSerializationEnv(null)
}

fun createTestSerializationEnv(classLoader: ClassLoader?): SerializationEnvironment {
    var customCheckpointSerializers: Set<CheckpointCustomSerializer<*, *>> = emptySet()
    val (clientSerializationScheme, serverSerializationScheme) = if (classLoader != null) {
        val customSerializers = createInstancesOfClassesImplementing(classLoader, SerializationCustomSerializer::class.java)
        customCheckpointSerializers = createInstancesOfClassesImplementing(classLoader, CheckpointCustomSerializer::class.java)

        val serializationWhitelists = ServiceLoader.load(SerializationWhitelist::class.java, classLoader).toSet()

        Pair(AMQPClientSerializationScheme(customSerializers, serializationWhitelists),
             AMQPServerSerializationScheme(customSerializers, serializationWhitelists))
    } else {
        Pair(AMQPClientSerializationScheme(emptyList()), AMQPServerSerializationScheme(emptyList()))
    }
    val factory = SerializationFactoryImpl().apply {
        registerScheme(clientSerializationScheme)
        registerScheme(serverSerializationScheme)
    }
    return SerializationEnvironment.with(
            factory,
            AMQP_P2P_CONTEXT,
            AMQP_RPC_SERVER_CONTEXT,
            AMQP_RPC_CLIENT_CONTEXT,
            AMQP_STORAGE_CONTEXT,
            KRYO_CHECKPOINT_CONTEXT.withCheckpointCustomSerializers(customCheckpointSerializers),
            KryoCheckpointSerializer
    )
}

fun <T> SerializationEnvironment.asTestContextEnv(callable: (SerializationEnvironment) -> T): T {
    try {
        return asContextEnv(callable)
    } finally {
        inVMExecutors.remove(this)
    }
}
