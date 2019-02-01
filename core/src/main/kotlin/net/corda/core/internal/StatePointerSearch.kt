package net.corda.core.internal

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StatePointer
import net.corda.core.internal.reflection.LocalTypeModel
import net.corda.core.internal.reflection.ObjectGraphTraverser
import java.lang.IllegalStateException
import java.lang.reflect.Field
import java.util.*

/**
 * Uses reflection to search for instances of [StatePointer] within a [ContractState].
 */
class StatePointerSearch(val state: ContractState, val typeModel: LocalTypeModel = LocalTypeModel.unconstrained) {
    // Classes in these packages should not be part of a search.
    private val blackListedPackages = setOf("java.", "javax.", "org.bouncycastle.", "net.i2p.crypto.")

    fun search(): Set<StatePointer<*>> {
        return ObjectGraphTraverser.traverse(state, typeModel) {
            val packageName = it.javaClass.`package`?.name
            packageName == null || blackListedPackages.any {
                blacklistedPackageName -> packageName.startsWith(blacklistedPackageName)
            }
        }.filterIsInstance<StatePointer<*>>().toSet()
    }
}