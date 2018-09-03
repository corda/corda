package net.corda.testing.internal

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

private val familyToNextPoolNumber = ConcurrentHashMap<String, AtomicInteger>()
fun Any.testThreadFactory(useEnclosingClassName: Boolean = false): ThreadFactory {
    val poolFamily = javaClass.let { (if (useEnclosingClassName) it.enclosingClass else it).simpleName }
    val poolNumber = familyToNextPoolNumber.computeIfAbsent(poolFamily) { AtomicInteger(1) }.getAndIncrement()
    val nextThreadNumber = AtomicInteger(1)
    return ThreadFactory { task ->
        Thread(task, "$poolFamily-$poolNumber-${nextThreadNumber.getAndIncrement()}")
    }
}
