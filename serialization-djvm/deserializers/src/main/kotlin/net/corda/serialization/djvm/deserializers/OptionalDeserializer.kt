package net.corda.serialization.djvm.deserializers

import net.corda.serialization.internal.amqp.custom.OptionalSerializer.OptionalProxy
import java.util.*
import java.util.function.Function

class OptionalDeserializer : Function<OptionalProxy, Optional<Any>> {
    override fun apply(proxy: OptionalProxy): Optional<Any> {
        return Optional.ofNullable(proxy.item)
    }
}
