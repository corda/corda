package net.corda.core.internal

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * A [ConcurrentBox] allows the implementation of track() with reduced contention. [concurrent] may be run from several
 *   threads (which means it MUST be threadsafe!), while [exclusive] stops the world until the tracking has been set up.
 *   Internally [ConcurrentBox] is implemented simply as a read-write lock.
 */
class ConcurrentBox<out T>(val content: T) {
    val lock = ReentrantReadWriteLock()

    inline fun <R> concurrent(block: T.() -> R): R = lock.read { block(content) }
    inline fun <R> exclusive(block: T.() -> R): R = lock.write { block(content) }
}
