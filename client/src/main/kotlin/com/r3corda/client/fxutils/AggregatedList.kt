package com.r3corda.client.fxutils

import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.collections.transformation.TransformationList
import kotlin.comparisons.compareValues

/**
 * [AggregatedList] provides an [ObservableList] that is an aggregate of the underlying list based on key [K].
 * Internally it uses a sorted list TODO think of a more efficient representation
 */
class AggregatedList<A, E, K : Any>(
        list: ObservableList<out E>,
        val toKey: (E) -> K,
        val assemble: (K, ObservableList<E>) -> A
) : TransformationList<A, E>(list) {

    private class AggregationGroup<E, out A>(
            val key: Int,
            val value: A,
            val elements: ObservableList<E>
    )

    // Invariant: sorted by K
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
            if (c.wasPermutated()) {
                // Permutation should not change aggregation
            } else if (c.wasUpdated()) {
                // Update should not change aggregation
            } else {
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
        endChange()
    }

    private fun removeItem(removedItem: E): Pair<Int, AggregationGroup<E, A>>? {
        val key = toKey(removedItem)
        val keyHashCode = key.hashCode()

        val index = aggregationList.binarySearch(
                comparison = { group -> compareValues(keyHashCode, group.key.hashCode()) }
        )
        if (index < 0) {
            throw IllegalStateException("Removed element $removedItem does not map to an existing aggregation")
        } else {
            val aggregationGroup = aggregationList[index]
            if (aggregationGroup.elements.size == 1) {
                return Pair(index, aggregationList.removeAt(index))
            }
            aggregationGroup.elements.remove(removedItem)
        }
        return null
    }

    private fun addItem(addedItem: E): Int? {
        val key = toKey(addedItem)
        val keyHashCode = key.hashCode()
        val index = aggregationList.binarySearch(
                comparison = { group -> compareValues(keyHashCode, group.key.hashCode()) }
        )
        if (index < 0) {
            // New aggregation
            val observableGroupElements = FXCollections.observableArrayList<E>()
            observableGroupElements.add(addedItem)
            val aggregationGroup = AggregationGroup(
                    key = keyHashCode,
                    value = assemble(key, observableGroupElements),
                    elements = observableGroupElements
            )
            val insertIndex = -index - 1
            aggregationList.add(insertIndex, aggregationGroup)
            return insertIndex
        } else {
            aggregationList[index].elements.add(addedItem)
            return null
        }
    }
}
