package net.corda.core.internal

import kotlin.reflect.KProperty

/**
 * A write-once property to be used as delegate for Kotlin var properties.  The expectation is that this is initialised
 * prior to the spawning of any threads that may access it and so there's no need for it to be volatile.
 */
class WriteOnceProperty<T : Any>(private val defaultValue: T? = null) {
    private var v: T? = defaultValue

    operator fun getValue(thisRef: Any?, property: KProperty<*>) = v ?: throw IllegalStateException("Write-once property $property not set.")

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        check(v == defaultValue || v === value) { "Cannot set write-once property $property more than once." }
        v = value
    }
}