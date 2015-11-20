@file:JvmName("ContractTools")

package core

import java.util.*

/**
 * Utilities for contract writers to incorporate into their logic.
 */

data class InOutGroup<T : ContractState>(val inputs: List<T>, val outputs: List<T>)

// For Java users.
fun <T : ContractState> groupStates(ofType: Class<T>, allInputs: List<ContractState>,
                                    allOutputs: List<ContractState>, selector: (T) -> Any): List<InOutGroup<T>> {
    val inputs = allInputs.filterIsInstance(ofType)
    val outputs = allOutputs.filterIsInstance(ofType)

    val inGroups = inputs.groupBy(selector)
    val outGroups = outputs.groupBy(selector)

    @Suppress("DEPRECATION")
    return groupStatesInternal(inGroups, outGroups)
}

// For Kotlin users: this version has nicer syntax and avoids reflection/object creation for the lambda.
inline fun <reified T : ContractState> groupStates(allInputs: List<ContractState>,
                                                   allOutputs: List<ContractState>,
                                                   selector: (T) -> Any): List<InOutGroup<T>> {
    val inputs = allInputs.filterIsInstance<T>()
    val outputs = allOutputs.filterIsInstance<T>()

    val inGroups = inputs.groupBy(selector)
    val outGroups = outputs.groupBy(selector)

    @Suppress("DEPRECATION")
    return groupStatesInternal(inGroups, outGroups)
}

@Deprecated("Do not use this directly: exposed as public only due to function inlining")
fun <T : ContractState> groupStatesInternal(inGroups: Map<Any, List<T>>, outGroups: Map<Any, List<T>>): List<InOutGroup<T>> {
    val result = ArrayList<InOutGroup<T>>()

    for ((k, v) in inGroups.entries)
        result.add(InOutGroup(v, outGroups[k] ?: emptyList()))
    for ((k, v) in outGroups.entries) {
        if (inGroups[k] == null)
            result.add(InOutGroup(emptyList(), v))
    }

    return result
}
