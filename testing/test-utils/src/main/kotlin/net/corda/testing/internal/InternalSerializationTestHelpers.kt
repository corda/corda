package net.corda.testing.internal

import net.corda.nodeapi.internal.rpc.client.AMQPClientSerializationScheme
import net.corda.core.internal.createInstancesOfClassesImplementing
import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.nodeapi.internal.serilialization.amqp.AMQPServerSerializationScheme
import net.corda.nodeapi.internal.serilialization.kryo.KRYO_CHECKPOINT_CONTEXT
import net.corda.nodeapi.internal.serilialization.kryo.KryoCheckpointSerializer
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
    val clientSerializationScheme = if (classLoader != null) {
        val customSerializers = createInstancesOfClassesImplementing(classLoader, SerializationCustomSerializer::class.java)
        val serializationWhitelists = ServiceLoader.load(SerializationWhitelist::class.java, classLoader).toSet()
        AMQPClientSerializationScheme(customSerializers, serializationWhitelists)
    } else {
        AMQPClientSerializationScheme(emptyList())
    }
    val factory = SerializationFactoryImpl().apply {
        registerScheme(clientSerializationScheme)
        registerScheme(AMQPServerSerializationScheme(emptyList()))
    }
    return SerializationEnvironment.with(
            factory,
            AMQP_P2P_CONTEXT,
            AMQP_RPC_SERVER_CONTEXT,
            AMQP_RPC_CLIENT_CONTEXT,
            AMQP_STORAGE_CONTEXT,
            KRYO_CHECKPOINT_CONTEXT,
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
