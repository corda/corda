package net.corda.core.internal

import kotlin.reflect.KProperty

/** May go from null to non-null and vice-versa, and that's it. */
abstract class AbstractUnstackableProperty<T>(val name: String) {
    protected abstract fun getImpl(): T?
    protected abstract fun setImpl(value: T?)
    fun get() = synchronized(this, this::getImpl)
    fun set(value: T?) = synchronized(this) {
        if (value != null) {
            check(getImpl() == null) { "$name already has a value." }
        } else {
            check(getImpl() != null) { "$name is already null." }
        }
        setImpl(value)
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>) = get()
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) = set(value)
}

class UnstackableProperty<T>(name: String) : AbstractUnstackableProperty<T>(name) {
    private var value: T? = null
    override fun getImpl() = value
    override fun setImpl(value: T?) {
        this.value = value
    }
}

class UnstackableThreadLocal<T>(name: String) : AbstractUnstackableProperty<T>(name) {
    private val value = ThreadLocal<T?>() // Logically it's T but T? is safer.
    override fun getImpl() = value.get()
    override fun setImpl(value: T?) {
        this.value.set(value)
    }
}

class UnstackableInheritableThreadLocal<T>(name: String) : AbstractUnstackableProperty<T>(name) {
    private val value = InheritableThreadLocal<T?>()
    override fun getImpl() = value.get()
    override fun setImpl(value: T?) {
        this.value.set(value)
    }
}