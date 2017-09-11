package net.corda.nodeapi.internal.serialization.amqp.custom

import net.corda.nodeapi.internal.serialization.amqp.CustomSerializer
import net.corda.nodeapi.internal.serialization.amqp.MapSerializer
import net.corda.nodeapi.internal.serialization.amqp.SerializerFactory
import java.util.*

@Suppress("UNCHECKED_CAST")
/**
 * A serializer that writes out an [EnumSet] as a type, plus list of instances in the set.
 */
class EnumSetSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<EnumSet<*>, EnumSetSerializer.EnumSetProxy>(EnumSet::class.java, EnumSetProxy::class.java, factory) {
    override val additionalSerializers: Iterable<CustomSerializer<out Any>> = listOf(ClassSerializer(factory))

    override fun toProxy(obj: EnumSet<*>): EnumSetProxy = EnumSetProxy(elementType(obj), obj.toList())

    private fun elementType(set: EnumSet<*>): Class<*> {
        return if (set.isEmpty()) {
            EnumSet.complementOf(set as EnumSet<MapSerializer.EnumJustUsedForCasting>).first().javaClass
        } else {
            set.first().javaClass
        }
    }

    override fun fromProxy(proxy: EnumSetProxy): EnumSet<*> {
        return if (proxy.elements.isEmpty()) {
            EnumSet.noneOf(proxy.clazz as Class<MapSerializer.EnumJustUsedForCasting>)
        } else {
            EnumSet.copyOf(proxy.elements as List<MapSerializer.EnumJustUsedForCasting>)
        }
    }

    data class EnumSetProxy(val clazz: Class<*>, val elements: List<Any>)
}