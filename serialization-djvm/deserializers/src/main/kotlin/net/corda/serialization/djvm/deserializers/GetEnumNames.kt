package net.corda.serialization.djvm.deserializers

import java.util.function.Function

class GetEnumNames : Function<Array<Enum<*>>, Array<String>> {
    override fun apply(enumValues: Array<Enum<*>>): Array<String> {
        return enumValues.map(Enum<*>::name).toTypedArray()
    }
}
