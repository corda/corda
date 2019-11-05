package net.corda.serialization.djvm.deserializers

import java.util.function.Function

class DescribeEnum : Function<Class<*>, Array<out Any?>> {
    override fun apply(enumClass: Class<*>): Array<out Any?> {
        return enumClass.enumConstants
    }
}
