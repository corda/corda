package net.corda.core.internal

import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KProperty

/** May go from null to non-null and vice-versa, and that's it. */
abstract class ToggleField<T>(val name: String) {
    private val writeMutex = Any() // Protects the toggle logic only.
    abstract fun get(): T?
    fun set(value: T?) = synchronized(writeMutex) {
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

class SimpleToggleField<T>(name: String, private val once: Boolean = false) : ToggleField<T>(name) {
    private val holder = AtomicReference<T?>() // Force T? in API for safety.
    override fun get() = holder.get()
    override fun setImpl(value: T) = holder.set(value)
    override fun clear() {
        check(!once) { "Value of $name cannot be changed." }
        holder.set(null)
    }
}

class ThreadLocalToggleField<T>(name: String) : ToggleField<T>(name) {
    private val threadLocal = ThreadLocal<T?>()
    override fun get() = threadLocal.get()
    override fun setImpl(value: T) = threadLocal.set(value)
    override fun clear() = threadLocal.remove()
}

/** The named thread has leaked from a previous test. */
class ThreadLeakException : RuntimeException("Leaked thread detected: ${Thread.currentThread().name}")

class InheritableThreadLocalToggleField<T>(name: String) : ToggleField<T>(name) {
    private class Holder<T>(value: T) : AtomicReference<T?>(value) {
        fun valueOrDeclareLeak() = get() ?: throw ThreadLeakException()
    }

    private val threadLocal = object : InheritableThreadLocal<Holder<T>?>() {
        override fun childValue(holder: Holder<T>?): Holder<T>? {
            // The Holder itself may be null due to prior events, a leak is not implied in that case:
            holder?.valueOrDeclareLeak() // Fail fast.
            return holder // What super does.
        }
    }

    override fun get() = threadLocal.get()?.valueOrDeclareLeak()
    override fun setImpl(value: T) = threadLocal.set(Holder(value))
    override fun clear() = threadLocal.run {
        val holder = get()!!
        remove()
        holder.set(null) // Threads that inherited the holder are now considered to have escaped from the test.
    }
}
