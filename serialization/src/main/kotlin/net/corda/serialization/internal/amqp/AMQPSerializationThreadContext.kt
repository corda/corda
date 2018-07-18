@file:JvmName("AMQPSerializationThreadContext")
package net.corda.serialization.internal.amqp

fun getContextClassLoader(): ClassLoader {
    return Thread.currentThread().contextClassLoader
}
