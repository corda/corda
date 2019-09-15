package net.corda.djvm.serialization.deserializers

import java.util.function.Function

class DescribeEnum : Function<Class<*>, Array<out Any?>> {
    override fun apply(enumClass: Class<*>): Array<out Any?> {
        return enumClass.enumConstants
    }
}
