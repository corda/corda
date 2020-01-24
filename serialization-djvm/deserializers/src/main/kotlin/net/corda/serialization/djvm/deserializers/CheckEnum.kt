package net.corda.serialization.djvm.deserializers

import java.util.function.Predicate

class CheckEnum : Predicate<Class<*>> {
    override fun test(clazz: Class<*>): Boolean {
        return clazz.isEnum
    }
}
