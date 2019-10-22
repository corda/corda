package net.corda.serialization.djvm.deserializers

import java.lang.reflect.Constructor
import java.util.function.Function

class CreateFromString(private val factory: Constructor<Any>) : Function<String, Any> {
    override fun apply(text: String): Any {
        return factory.newInstance(text)
    }
}
