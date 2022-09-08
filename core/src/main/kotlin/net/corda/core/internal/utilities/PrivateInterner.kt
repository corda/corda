package net.corda.core.internal.utilities

import com.google.common.collect.Interners
import net.corda.core.internal.uncheckedCast

/**
 * This class converts instances supplied to [intern] to a common instance within the JVM, amongst all those
 * instances that have been submitted.  It uses weak references to avoid memory leaks.
 *
 * NOTE: the Guava interners are Beta, so upgrading Guava may result in us having to adapt this code.
 *
 * System properties allow disabling, in the event an issue is uncovered in a live environment.  The
 * correct default for the concurrency setting is the result of performance evaluation.
 */
class PrivateInterner<T>(val verifier: Verifier<T> = NoneVerifier()) {
    companion object {
        private const val DEFAULT_CONCURRENCY_LEVEL = 32
        private val CONCURRENCY_LEVEL = Integer.getInteger("net.corda.core.intern.concurrency", DEFAULT_CONCURRENCY_LEVEL).toInt()
        private val DISABLE = java.lang.Boolean.getBoolean("net.corda.core.intern.disable")
    }

    private val interner = Interners.newBuilder().weak().concurrencyLevel(CONCURRENCY_LEVEL).build<T>()

    fun <S : T> intern(sample: S): S = if (DISABLE) sample else uncheckedCast(verifier.choose(sample, interner.intern(sample)))
}

