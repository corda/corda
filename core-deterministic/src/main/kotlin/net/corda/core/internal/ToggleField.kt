package net.corda.core.internal

import net.corda.core.KeepForDJVM
import net.corda.core.utilities.contextLogger
import org.slf4j.Logger
import kotlin.reflect.KProperty

/** May go from null to non-null and vice-versa, and that's it. */
abstract class ToggleField<T>(val name: String) {
    abstract fun get(): T?
    fun set(value: T?) {
        if (value != null) {
            check(get() == null) { "$name already has a value." }
            setImpl(value)
        } else {
            check(get() != null) { "$name is already null." }
            clear()
        }
    }

    protected abstract fun setImpl(value: T)
    protected abstract fun clear()
    operator fun getValue(thisRef: Any?, property: KProperty<*>) = get()
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) = set(value)
}

@KeepForDJVM
class SimpleToggleField<T>(name: String, private val once: Boolean = false) : ToggleField<T>(name) {
    private var holder: T? = null // Force T? in API for safety.
    override fun get() = holder
    override fun setImpl(value: T) { holder = value }
    override fun clear() {
        check(!once) { "Value of $name cannot be changed." }
        holder = null
    }
}

@KeepForDJVM
class ThreadLocalToggleField<T>(name: String) : ToggleField<T>(name) {
    private var holder: T? = null // Force T? in API for safety.
    override fun get() = holder
    override fun setImpl(value: T) { holder = value }
    override fun clear() {
        holder = null
    }
}

@Suppress("UNUSED")
@KeepForDJVM
class InheritableThreadLocalToggleField<T>(name: String,
                                           private val log: Logger = staticLog,
                                           private val isAGlobalThreadBeingCreated: (Array<StackTraceElement>) -> Boolean) : ToggleField<T>(name) {
    private companion object {
        private val staticLog = contextLogger()
    }
    private var holder: T? = null // Force T? in API for safety.
    override fun get() = holder
    override fun setImpl(value: T) { holder = value }
    override fun clear() {
        holder = null
    }
}