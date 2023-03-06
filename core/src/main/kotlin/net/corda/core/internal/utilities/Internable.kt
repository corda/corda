package net.corda.core.internal.utilities

import net.corda.core.CordaInternal
import net.corda.core.KeepForDJVM

@KeepForDJVM
interface Internable<T> {
    @CordaInternal
    val interner: PrivateInterner<T>
}

@KeepForDJVM
@CordaInternal
interface IternabilityVerifier<T> {
    // If a type being interned has a slightly dodgy equality check, the more strict rules you probably
    // want to apply to interning can be enforced here.
    fun choose(original: T, interned: T): T
}

@KeepForDJVM
@CordaInternal
class AlwaysInternableVerifier<T> : IternabilityVerifier<T> {
    override fun choose(original: T, interned: T): T = interned
}