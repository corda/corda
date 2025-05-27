package net.corda.node

import java.io.ObjectInputFilter
import java.io.ObjectInputFilter.Status

internal object SerialFilter {
    internal fun install(acceptClass: (Class<*>) -> Boolean) {
        ObjectInputFilter.Config.setSerialFilter { filterInfo ->
            if (applyPredicate(acceptClass, filterInfo.serialClass())) Status.UNDECIDED else Status.REJECTED
        }
    }

    internal fun applyPredicate(acceptClass: (Class<*>) -> Boolean, serialClass: Class<*>?): Boolean {
        // Similar logic to jdk.serialFilter, our concern is side-effects at deserialisation time:
        if (null == serialClass) return true
        var componentType: Class<*> = serialClass
        while (componentType.isArray) componentType = componentType.componentType
        if (componentType.isPrimitive) return true
        return acceptClass(componentType)
    }
}

internal fun defaultSerialFilter(@Suppress("UNUSED_PARAMETER") clazz: Class<*>) = false
