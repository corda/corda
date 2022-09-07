package net.corda.core.internal.utilities

import com.google.common.collect.Interners

class PrivateInterner<T> {
    private val interner = Interners.newBuilder().weak().concurrencyLevel(32).build<T>()

    fun <S : T> intern(sample: S): S = interner.intern(sample) as S
}

