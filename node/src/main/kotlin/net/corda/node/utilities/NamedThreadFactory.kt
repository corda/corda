package net.corda.node.utilities

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

class NamedThreadFactory(private val name:String, private val underlyingFactory: ThreadFactory) : ThreadFactory{
    val threadNumber = AtomicInteger(1)
    override fun newThread(r: Runnable?): Thread {
        val t = underlyingFactory.newThread(r)
        t.name = name + "-" + threadNumber.getAndIncrement()
        return t
    }
}

fun newNamedSingleThreadExecutor(name: String): ExecutorService {
    return Executors.newSingleThreadExecutor(NamedThreadFactory(name, Executors.defaultThreadFactory()))
}
