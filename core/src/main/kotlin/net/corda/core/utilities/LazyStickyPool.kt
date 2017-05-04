package net.corda.core.utilities

import java.util.*
import java.util.concurrent.LinkedBlockingQueue

/**
 * A [LazyStickyPool] is a lazy pool of resources where a [borrow] may "stick" the borrowed instance to an object.
 * Any subsequent borrows using the same object will return the same pooled instance.
 *
 * @param size The size of the pool.
 * @param shouldReturnToPool If specified this function will be run on each release to determine whether the instance
 *     should be returned to the pool for reuse. This may be useful for pooled resources that dynamically grow during
 *     usage, and we may not want to retain them forever.
 * @param newInstance The function to call to create a pooled resource.
 */
// TODO This could be implemented more efficiently. Currently the "non-sticky" use case is not optimised, it just chooses a random instance to wait on.
class LazyStickyPool<A : Any>(
        size: Int,
        private val shouldReturnToPool: ((A) -> Boolean)? = null,
        private val newInstance: () -> A
) {
    private class InstanceBox<A> {
        var instance: LinkedBlockingQueue<A>? = null
    }
    private val random = Random()
    private val boxes = Array(size) { InstanceBox<A>() }

    private fun toIndex(stickTo: Any): Int {
        return Math.abs(stickTo.hashCode()) % boxes.size
    }

    fun borrow(stickTo: Any): A {
        val box = boxes[toIndex(stickTo)]
        val instance = synchronized(box) {
            val instance = box.instance
            if (instance == null) {
                val newInstance = LinkedBlockingQueue(listOf(newInstance()))
                box.instance = newInstance
                newInstance
            } else {
                instance
            }
        }
        return instance.take()
    }

    fun borrow(): Pair<Any, A> {
        val randomInt = random.nextInt()
        val instance = borrow(randomInt)
        return Pair(randomInt, instance)
    }

    fun release(stickTo: Any, instance: A) {
        val box = boxes[toIndex(stickTo)]
        if (shouldReturnToPool == null || shouldReturnToPool.invoke(instance)) {
            box.instance!!.add(instance)
        } else  {
            // We need to create a new instance instead of setting the queue to null to unblock potentially waiting threads.
            box.instance!!.add(newInstance())
        }
    }

    inline fun <R> run(stickToOrNull: Any? = null, withInstance: (A) -> R): R {
        val (stickTo, instance) = if (stickToOrNull == null) {
            borrow()
        } else {
            Pair(stickToOrNull, borrow(stickToOrNull))
        }
        try {
            return withInstance(instance)
        } finally {
            release(stickTo, instance)
        }
    }

    fun close(): Iterable<A> {
        return boxes.map { it.instance?.poll() }.filterNotNull()
    }
}