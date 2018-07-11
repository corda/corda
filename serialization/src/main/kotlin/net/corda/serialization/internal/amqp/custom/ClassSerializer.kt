package net.corda.serialization.internal.amqp.custom

import net.corda.core.KeepForDJVM
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.custom.ClassSerializer.ClassProxy

/**
 * A serializer for [Class] that uses [ClassProxy] proxy object to write out
 */
class ClassSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<Class<*>, ClassSerializer.ClassProxy>(Class::class.java, ClassProxy::class.java, factory) {
    override fun toProxy(obj: Class<*>): ClassProxy = ClassProxy(obj.name)

    override fun fromProxy(proxy: ClassProxy): Class<*> = Class.forName(proxy.className, true, factory.classloader)

    @KeepForDJVM
    data class ClassProxy(val className: String)
}