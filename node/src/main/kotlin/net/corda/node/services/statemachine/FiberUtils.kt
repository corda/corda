package net.corda.node.services.statemachine

import co.paralleluniverse.concurrent.util.ThreadAccess
import co.paralleluniverse.fibers.Fiber
import java.lang.reflect.Field

private val fiberThreadLocalsField: Field = Fiber::class.java.getDeclaredField("fiberLocals").apply { this.isAccessible = true }

private fun <V> Fiber<V>.swappedOutThreadLocals(): Any = fiberThreadLocalsField.get(this)

fun <V, T> Fiber<V>.swappedOutThreadLocalValue(threadLocal: ThreadLocal<T>): T? {
    val threadLocals = swappedOutThreadLocals()
    return ThreadAccess.toMap(threadLocals)[threadLocal] as T?
}
