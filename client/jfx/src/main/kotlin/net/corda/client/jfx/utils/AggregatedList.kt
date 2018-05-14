/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.client.jfx.utils

import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.collections.transformation.TransformationList

/**
 * Given an [ObservableList]<[E]> and a grouping key [K], [AggregatedList] groups the elements by the key into a fresh
 * [ObservableList]<[E]> for each group and exposes the groups as an observable list of [A]s by calling [assemble] on each.
 *
 * Changes done to elements of the input list are reflected in the observable list of the respective group, whereas
 * additions/removals of elements in the underlying list are reflected in the exposed [ObservableList]<[A]> by
 * adding/deleting aggregations as expected.
 *
 * The ordering of the exposed list is based on the [hashCode] of keys.
 * The ordering of the groups themselves is based on the [hashCode] of elements.
 *
 * Warning: If there are two elements [E] in the source list that have the same [hashCode] then it is not deterministic
 * which one will be removed if one is removed from the source list!
 *
 * Example:
 *   val statesGroupedByCurrency = AggregatedList(states, { state -> state.currency }) { currency, group ->
 *     object {
 *       val currency = currency
 *       val states = group
 *     }
 *   }
 *
 * The above creates an observable list of (currency, statesOfCurrency) pairs.
 *
 * Note that update events to the source list are discarded, assuming the key of elements does not change.
 * TODO Should we handle this case? It requires additional bookkeeping of sourceIndex->(aggregationIndex, groupIndex)
 *
 * @param list The underlying list.
 * @param toKey Function to extract the key from an element.
 * @param assemble Function to assemble the aggregation into the exposed [A].
 */
class AggregatedList<A, E : Any, K : Any>(
        list: ObservableList<out E>,
        val toKey: (E) -> K,
        val assemble: (K, ObservableList<E>) -> A
) : TransformationList<A, E>(list) {

    private class AggregationGroup<E, out A>(
            val keyHashCode: Int,
            val value: A,
            // Invariant: sorted by E.hashCode()
            val elements: ObservableList<E>
    )

    // Invariant: sorted by K.hashCode()
    private val aggregationList = mutableListOf<AggregationGroup<E, A>>()

    init {
        list.forEach { addItem(it) }
    }

    override fun get(index: Int): A? = aggregationList.getOrNull(index)?.value

    /**
     * We cannot implement this as aggregations are one to many
     */
    override fun getSourceIndex(index: Int): Int {
        throw UnsupportedOperationException()
    }

    override val size: Int get() = aggregationList.size

    override fun sourceChanged(c: ListChangeListener.Change<out E>) {
        beginChange()
        while (c.next()) {
            when {
                c.wasPermutated() -> {
                    // Permutation should not change aggregation
                }
                c.wasUpdated() -> {
                    // Update should not change aggregation
                }
                else -> {
                    for (removedSourceItem in c.removed) {
                        val removedPair = removeItem(removedSourceItem)
                        if (removedPair != null) {
                            nextRemove(removedPair.first, removedPair.second.value)
                        }
                    }
                    for (addedItem in c.addedSubList) {
                        val insertIndex = addItem(addedItem)
                        if (insertIndex != null) {
                            nextAdd(insertIndex, insertIndex + 1)
                        }
                    }
                }
            }
        }
        endChange()
    }

    private fun removeItem(removedItem: E): Pair<Int, AggregationGroup<E, A>>? {
        val key = toKey(removedItem)
        val keyHashCode = key.hashCode()

        val index = aggregationList.binarySearch(
                comparison = { group -> compareValues(keyHashCode, group.keyHashCode.hashCode()) }
        )
        if (index < 0) {
            throw IllegalStateException("Removed element $removedItem does not map to an existing aggregation")
        } else {
            val aggregationGroup = aggregationList[index]
            if (aggregationGroup.elements.size == 1) {
                return Pair(index, aggregationList.removeAt(index))
            }
            val elementHashCode = removedItem.hashCode()
            val removeIndex = aggregationGroup.elements.binarySearch(
                    comparison = { element -> compareValues(elementHashCode, element.hashCode()) }
            )
            if (removeIndex < 0) {
                throw IllegalStateException("Cannot find removed element $removedItem in group")
            } else {
                aggregationGroup.elements.removeAt(removeIndex)
            }
        }
        return null
    }

    private fun addItem(addedItem: E): Int? {
        val key = toKey(addedItem)
        val keyHashCode = key.hashCode()
        val aggregationIndex = aggregationList.binarySearch(
                comparison = { group -> compareValues(keyHashCode, group.keyHashCode.hashCode()) }
        )
        if (aggregationIndex < 0) {
            // New aggregation
            val observableGroupElements = FXCollections.observableArrayList<E>()
            observableGroupElements.add(addedItem)
            val aggregationGroup = AggregationGroup(
                    keyHashCode = keyHashCode,
                    value = assemble(key, observableGroupElements),
                    elements = observableGroupElements
            )
            val insertIndex = -aggregationIndex - 1
            aggregationList.add(insertIndex, aggregationGroup)
            return insertIndex
        } else {
            val elements = aggregationList[aggregationIndex].elements
            val elementHashCode = addedItem.hashCode()
            val elementIndex = elements.binarySearch(
                    comparison = { element -> compareValues(elementHashCode, element.hashCode()) }
            )
            val addIndex = if (elementIndex < 0) {
                -elementIndex - 1
            } else {
                // There is an existing element with the same hash (which is fine)
                elementIndex
            }
            elements.add(addIndex, addedItem)
            return null
        }
    }
}
