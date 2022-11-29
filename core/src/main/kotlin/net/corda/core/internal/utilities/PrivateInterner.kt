package net.corda.core.internal.utilities

import com.google.common.collect.Interners
import net.corda.core.CordaInternal
import net.corda.core.internal.packageNameOrNull
import net.corda.core.internal.uncheckedCast
import net.corda.core.serialization.CordaSerializable
import kotlin.reflect.full.companionObjectInstance

/**
 * This class converts instances supplied to [intern] to a common instance within the JVM, amongst all those
 * instances that have been submitted.  It uses weak references to avoid memory leaks.
 *
 * NOTE: the Guava interners are Beta, so upgrading Guava may result in us having to adapt this code.
 *
 * System properties allow disabling, in the event an issue is uncovered in a live environment.  The
 * correct default for the concurrency setting is the result of performance evaluation.
 */
@CordaInternal
class PrivateInterner<T>(val verifier: IternabilityVerifier<T> = AlwaysInternableVerifier()) {
    companion object {
        // This value is the default in Guava, and performance testing didn't reveal a need to change
        private const val DEFAULT_CONCURRENCY_LEVEL = 4
        private val CONCURRENCY_LEVEL = Integer.getInteger("net.corda.core.intern.concurrency", DEFAULT_CONCURRENCY_LEVEL).toInt()
        private val DISABLE = java.lang.Boolean.getBoolean("net.corda.core.intern.disable")

        /**
         * This will look at the companion object of a class, and on the super class companion object,
         * to see if they implement [Internable], in which case there is a [PrivateInterner] instance
         * available to do interning.  Tolerant of null class references and a lack of companion objects.
         */
        @Suppress("ComplexMethod")
        fun findFor(clazz: Class<*>?): PrivateInterner<Any>? {
            fun hasCordaSerializable(type: Class<*>): Boolean {
                return type.isAnnotationPresent(CordaSerializable::class.java)
                        || type.interfaces.any(::hasCordaSerializable)
                        || (type.superclass != null && hasCordaSerializable(type.superclass))
            }

            fun isSerializableCore(clazz: Class<*>): Boolean {
                if (!(clazz.packageNameOrNull?.startsWith("net.corda.core") ?: false)) return false
                return hasCordaSerializable(clazz)
            }

            fun findInterner(clazz: Class<*>?): PrivateInterner<Any>? {
                // Kotlin reflection has a habit of throwing exceptions, so protect just in case.
                try {
                    return clazz?.kotlin?.companionObjectInstance?.let {
                        (it as? Internable<*>)?.let {
                            uncheckedCast(it.interner)
                        }
                    }
                } catch (_: Throwable) {
                    return null
                }
            }
            return if (clazz != null) {
                // We try not to ruffle the feathers of kotlin reflection by avoiding throwing all types at it.
                if (!isSerializableCore(clazz)) return null
                findInterner(clazz) ?: findInterner(clazz.superclass)
            } else null
        }
    }

    private val interner = Interners.newBuilder().weak().concurrencyLevel(CONCURRENCY_LEVEL).build<T>()

    fun <S : T> intern(sample: S): S = if (DISABLE) sample else uncheckedCast(verifier.choose(sample, interner.intern(sample)))
}

