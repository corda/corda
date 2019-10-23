package net.corda.serialization.djvm.deserializers

import net.corda.core.internal.uncheckedCast
import net.corda.serialization.internal.amqp.custom.EnumSetSerializer.EnumSetProxy
import java.util.EnumSet
import java.util.function.Function

class EnumSetDeserializer : Function<EnumSetProxy, EnumSet<*>> {
    override fun apply(proxy: EnumSetProxy): EnumSet<*> {
        return if (proxy.elements.isEmpty()) {
            EnumSet.noneOf(uncheckedCast<Class<*>, Class<JustForCasting>>(proxy.clazz))
        } else {
            EnumSet.copyOf(uncheckedCast<List<Any>, List<JustForCasting>>(proxy.elements))
        }
    }
}
