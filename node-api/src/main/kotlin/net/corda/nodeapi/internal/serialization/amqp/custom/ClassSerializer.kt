package net.corda.nodeapi.internal.serialization.amqp.custom

import net.corda.nodeapi.internal.serialization.amqp.CustomSerializer
import net.corda.nodeapi.internal.serialization.amqp.SerializerFactory

/**
 * A serializer for [Class] that uses [ClassProxy] proxy object to write out
 */
class ClassSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<Class<*>, ClassSerializer.ClassProxy>(Class::class.java, ClassProxy::class.java, factory) {
    override fun toProxy(obj: Class<*>): ClassProxy = ClassProxy(obj.name)

    override fun fromProxy(proxy: ClassProxy): Class<*> = Class.forName(proxy.className, true, factory.classloader)

    data class ClassProxy(val className: String)
}