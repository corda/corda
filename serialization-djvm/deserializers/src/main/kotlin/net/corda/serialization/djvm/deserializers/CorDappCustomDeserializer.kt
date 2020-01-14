package net.corda.serialization.djvm.deserializers

import net.corda.core.serialization.SerializationCustomSerializer
import java.util.function.Function

class CorDappCustomDeserializer(private val serializer: SerializationCustomSerializer<Any?, Any?>) : Function<Any?, Any?> {
    override fun apply(input: Any?): Any? {
        return serializer.fromProxy(input)
    }
}
