package net.corda.serialization.djvm.deserializers

import net.corda.serialization.internal.amqp.custom.ClassSerializer.ClassProxy

import java.util.function.Function

class ClassDeserializer : Function<ClassProxy, Class<*>> {
    override fun apply(proxy: ClassProxy): Class<*> {
        return Class.forName(proxy.className)
    }
}
