package net.corda.core.internal.utilities

import net.corda.core.CordaInternal
import net.corda.core.KeepForDJVM

@KeepForDJVM
interface Internable<T> {
    @CordaInternal
    val interner: PrivateInterner<T>
}