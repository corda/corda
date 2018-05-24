package net.corda.sandbox.execution

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Helper class for processing queued entities.
 */
@Suppress("MemberVisibilityCanBePrivate")
class QueueProcessor<T>(
        private val deduplicationKeyExtractor: (T) -> String,
        vararg elements: T
) {

    private val queue = ConcurrentLinkedQueue<T>(elements.toMutableList())

    private val seenElements = mutableSetOf<String>()

    /**
     * Add an element to the queue.
     */
    fun enqueue(element: T) {
        val key = deduplicationKeyExtractor(element)
        if (key !in seenElements) {
            queue.add(element)
            seenElements.add(key)
        }
    }

    /**
     * Remove one element from the queue.
     */
    fun dequeue(): T = queue.remove()

    /**
     * Check if queue is empty.
     */
    fun isNotEmpty() = queue.isNotEmpty()

    /**
     * Process the current queue with provided action per element.
     */
    inline fun process(action: QueueProcessor<T>.(T) -> Unit) {
        while (isNotEmpty()) {
            val element = dequeue()
            action(this, element)
        }
    }

}