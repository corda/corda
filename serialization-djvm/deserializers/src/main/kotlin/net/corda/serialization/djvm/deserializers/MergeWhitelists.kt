package net.corda.serialization.djvm.deserializers

import net.corda.core.serialization.SerializationWhitelist
import java.util.function.Function

class MergeWhitelists : Function<Array<SerializationWhitelist>, Array<Class<*>>> {
    override fun apply(whitelists: Array<SerializationWhitelist>): Array<Class<*>> {
        return whitelists.flatMapTo(LinkedHashSet(), SerializationWhitelist::whitelist).toTypedArray()
    }
}