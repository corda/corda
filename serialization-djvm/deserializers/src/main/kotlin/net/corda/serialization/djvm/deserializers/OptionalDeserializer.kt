package net.corda.serialization.djvm.deserializers

import net.corda.serialization.internal.amqp.custom.OptionalSerializer.OptionalProxy
import java.util.Optional
import java.util.function.Function

class OptionalDeserializer : Function<OptionalProxy, Optional<out Any>> {
    override fun apply(proxy: OptionalProxy): Optional<out Any> {
        return Optional.ofNullable(proxy.item)
    }
}
