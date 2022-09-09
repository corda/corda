package net.corda.core.internal.utilities

import net.corda.core.KeepForDJVM

@KeepForDJVM
class PrivateInterner<T>(val verifier: IternabilityVerifier<T> = AlwaysInternableVerifier()) {
    // DJVM implementation does not intern and does not use Guava
    fun <S : T> intern(sample: S): S = sample

    @KeepForDJVM
    companion object {
        fun findFor(clazz: Class<*>?): PrivateInterner<Any>? = null
    }
}

