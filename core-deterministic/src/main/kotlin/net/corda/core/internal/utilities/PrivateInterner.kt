package net.corda.core.internal.utilities

import net.corda.core.KeepForDJVM

@KeepForDJVM
class PrivateInterner<T>(val verifier: Verifier<T> = NoneVerifier()) {
    // DJVM implementation does not intern and does not use Guava
    fun <S : T> intern(sample: S): S = sample
}

