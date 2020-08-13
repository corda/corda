package net.corda.node.flows

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch

/**
 * Helper class for waiting until another thread has put a set number of objects
 * into a queue.
 */
internal class QueueWithCountdown<E>(count: Int = 0): Collection<E> {
    private val latch: CountDownLatch = CountDownLatch(count)
    private val queue: ConcurrentLinkedQueue<E> = ConcurrentLinkedQueue()

    fun add(element: E) {
        this.queue.add(element)
        this.latch.countDown()
    }

    fun await() = this.latch.await()

    override fun contains(element: E) = queue.contains(element)
    override fun containsAll(elements: Collection<E>) = queue.containsAll(elements)
    override fun isEmpty(): Boolean = queue.isEmpty()
    override fun iterator() = queue.iterator()
    override fun parallelStream() = queue.parallelStream()
    override fun spliterator() = queue.spliterator()
    override fun stream() = queue.stream()
    override val size: Int
            get() = queue.size
}