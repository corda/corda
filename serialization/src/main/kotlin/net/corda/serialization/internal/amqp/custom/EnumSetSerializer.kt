package net.corda.serialization.internal.amqp.custom

import net.corda.core.internal.uncheckedCast
import net.corda.serialization.internal.amqp.CustomSerializer
import net.corda.serialization.internal.amqp.MapSerializer
import net.corda.serialization.internal.amqp.SerializerFactory
import java.util.*

/**
 * A serializer that writes out an [EnumSet] as a type, plus list of instances in the set.
 */
class EnumSetSerializer(factory: SerializerFactory) : CustomSerializer.Proxy<EnumSet<*>, EnumSetSerializer.EnumSetProxy>(EnumSet::class.java, EnumSetProxy::class.java, factory) {
    override val additionalSerializers: Iterable<CustomSerializer<out Any>> = listOf(ClassSerializer(factory))

    override fun toProxy(obj: EnumSet<*>): EnumSetProxy = EnumSetProxy(elementType(obj), obj.toList())

    private fun elementType(set: EnumSet<*>): Class<*> {
        return if (set.isEmpty()) {
            EnumSet.complementOf(uncheckedCast<EnumSet<*>, EnumSet<MapSerializer.EnumJustUsedForCasting>>(set)).first().javaClass
        } else {
            set.first().javaClass
        }
    }

    override fun fromProxy(proxy: EnumSetProxy): EnumSet<*> {
        return if (proxy.elements.isEmpty()) {
            EnumSet.noneOf(uncheckedCast<Class<*>, Class<MapSerializer.EnumJustUsedForCasting>>(proxy.clazz))
        } else {
            EnumSet.copyOf(uncheckedCast<List<Any>, List<MapSerializer.EnumJustUsedForCasting>>(proxy.elements))
        }
    }

    data class EnumSetProxy(val clazz: Class<*>, val elements: List<Any>)
}