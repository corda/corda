package net.corda.node

import java.io.ObjectInputFilter
import java.io.ObjectInputFilter.Status

internal object SerialFilter {
    @Volatile
    private var acceptClass: ((Class<*>) -> Boolean)? = null

    /**
     * Installs the JVM-wide serialization filter, or replaces the predicate of the one already installed. The JDK only allows the filter
     * to be set once, but in dev mode a notary service implementation (e.g. BFT-SMaRt) replaces the node's default filter.
     */
    @Synchronized
    internal fun install(acceptClass: (Class<*>) -> Boolean) {
        val previous = this.acceptClass
        this.acceptClass = acceptClass
        if (previous == null) {
            try {
                ObjectInputFilter.Config.setSerialFilter { filterInfo -> status(filterInfo.serialClass()) }
            } catch (e: Exception) {
                this.acceptClass = null
                throw e
            }
        }
    }

    private fun status(serialClass: Class<*>?): Status {
        val acceptClass = this.acceptClass ?: return Status.REJECTED
        return if (applyPredicate(acceptClass, serialClass)) Status.UNDECIDED else Status.REJECTED
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
