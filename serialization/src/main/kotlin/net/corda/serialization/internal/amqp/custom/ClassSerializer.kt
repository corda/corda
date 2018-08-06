package net.corda.serialization.internal.amqp.custom

import net.corda.core.KeepForDJVM
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.trace
import net.corda.serialization.internal.amqp.AMQPNotSerializableException
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.custom.ClassSerializer.ClassProxy

/**
 * A serializer for [Class] that uses [ClassProxy] proxy object to write out
 */
class ClassSerializer(
        factory: SerializerFactory
) : CustomSerializer.Proxy<Class<*>, ClassSerializer.ClassProxy>(
        Class::class.java,
        ClassProxy::class.java,
        factory
) {
    companion object {
        private val logger = contextLogger()
    }

    override fun toProxy(obj: Class<*>): ClassProxy  {
        logger.trace { "serializer=custom, type=ClassSerializer, name=\"${obj.name}\", action=toProxy" }
        return ClassProxy(obj.name)
    }

    override fun fromProxy(proxy: ClassProxy): Class<*> {
        logger.trace { "serializer=custom, type=ClassSerializer, name=\"${proxy.className}\", action=fromProxy" }

        return try {
            Class.forName(proxy.className, true, factory.classloader)
        } catch (e: ClassNotFoundException) {
            throw AMQPNotSerializableException(
                    type,
                    "Could not instantiate ${proxy.className} - not on the classpath",
                    "${proxy.className} was not found by the node, check the Node containing the CorDapp that " +
                            "implements ${proxy.className} is loaded and on the Classpath",
                    mutableListOf(proxy.className))
        }
    }

    @KeepForDJVM
    data class ClassProxy(val className: String)
}