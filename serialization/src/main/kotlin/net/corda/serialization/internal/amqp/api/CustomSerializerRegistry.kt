package net.corda.serialization.internal.amqp.api

import net.corda.serialization.internal.amqp.serializers.CorDappCustomSerializer
import net.corda.serialization.internal.amqp.serializers.CustomSerializer
import net.corda.serialization.internal.amqp.utils.asClass
import net.corda.serialization.internal.model.CustomTypeDescriptorLookup
import java.lang.reflect.Type

interface CustomSerializerRegistry : CustomTypeDescriptorLookup {
    /**
     * Register a custom serializer for any type that cannot be serialized or deserialized by the default serializer
     * that expects to find getters and a constructor with a parameter for each property.
     */
    fun register(customSerializer: CustomSerializer<out Any>)
    fun registerExternal(customSerializer: CorDappCustomSerializer)

    fun findCustomSerializer(clazz: Class<*>, declaredType: Type): AMQPSerializer<Any>?

    override fun getCustomTypeDescriptor(type: Type) =
        findCustomSerializer(type.asClass(), type)?.typeDescriptor?.toString()
}