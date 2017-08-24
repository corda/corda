package net.corda.core.concurrent

import net.corda.core.utilities.getOrThrow

/** A future with additional methods to complete it with a value, exception or the outcome of another future. This
 * is separate to [CordaFuture] such that under normal usage futures cannot be set where they're meant to be used as
 * read-only.
 */
interface OpenFuture<V> : CordaFuture<V> {
    /** @return whether this future actually changed. */
    fun set(value: V): Boolean

    /** @return whether this future actually changed. */
    fun setException(t: Throwable): Boolean

    /** When the given future has an outcome, make this future have the same outcome. */
    fun captureLater(f: CordaFuture<out V>) = f.then { capture { f.getOrThrow() } }

    /** Run the given block (in the foreground) and set this future to its outcome. */
    fun capture(block: () -> V): Boolean {
        return set(try {
            block()
        } catch (t: Throwable) {
            return setException(t)
        })
    }
}