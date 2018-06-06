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

import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.collections.transformation.TransformationList
import java.util.*

/**
 * [FlattenedList] flattens the passed in list of [ObservableValue]s so that changes in individual updates to the values
 * are reflected in the exposed list as expected.
 */
class FlattenedList<A>(val sourceList: ObservableList<out ObservableValue<out A>>) : TransformationList<A, ObservableValue<out A>>(sourceList) {

    /**
     * We maintain an ObservableValue->index map. This is needed because we need the ObservableValue's index in order to
     * propagate a change and if the listener closure captures the index at the time of the call to
     * [ObservableValue.addListener] it will become incorrect if the indices shift around later.
     *
     * Note that because of the bookkeeping required for this map, any remove operation and any add operation that
     * inserts to the middle of the list will be O(N) as we need to scan the map and shift indices accordingly.
     *
     * Note also that we're wrapping each ObservableValue, this is required because we want to support reusing of
     * ObservableValues and we need each to have a different hash.
     */
    class WrappedObservableValue<A>(
            val observableValue: ObservableValue<A>
    )

    val indexMap = HashMap<WrappedObservableValue<out A>, Pair<Int, ChangeListener<A>>>()

    init {
        sourceList.forEachIndexed { index, observableValue ->
            val wrappedObservableValue = WrappedObservableValue(observableValue)
            indexMap[wrappedObservableValue] = Pair(index, createListener(wrappedObservableValue))
        }
    }

    private fun createListener(wrapped: WrappedObservableValue<out A>): ChangeListener<A> {
        val listener = ChangeListener<A> { _, oldValue, _ ->
            val currentIndex = indexMap[wrapped]!!.first
            beginChange()
            nextReplace(currentIndex, currentIndex + 1, listOf(oldValue))
            endChange()
        }
        wrapped.observableValue.addListener(listener)
        return listener
    }

    override fun sourceChanged(c: ListChangeListener.Change<out ObservableValue<out A>>) {
        beginChange()
        while (c.next()) {
            if (c.wasPermutated()) {
                val from = c.from
                val to = c.to
                val permutation = IntArray(to, { c.getPermutation(it) })
                indexMap.replaceAll { _, (first, second) -> Pair(permutation[first], second) }
                nextPermutation(from, to, permutation)
            } else if (c.wasUpdated()) {
                throw UnsupportedOperationException("FlattenedList doesn't support Update changes")
            } else {
                val removed = c.removed
                if (removed.size != 0) {
                    // TODO this assumes that if wasAdded() == true then we are adding elements to the getFrom() position
                    val removeStart = c.from
                    val removeRange = c.removed.size
                    val removeEnd = c.from + removeRange
                    val iterator = indexMap.iterator()
                    for (entry in iterator) {
                        val (wrapped, pair) = entry
                        val (index, listener) = pair
                        if (index >= removeStart) {
                            if (index < removeEnd) {
                                wrapped.observableValue.removeListener(listener)
                                iterator.remove()
                            } else {
                                // Shift indices
                                entry.setValue(Pair(index - removeRange, listener))
                            }
                        }
                    }
                    nextRemove(removeStart, removed.map { it.value })
                }
                if (c.wasAdded()) {
                    val addStart = c.from
                    val addEnd = c.to
                    val addRange = addEnd - addStart
                    // If it was a push to the end we don't need to shift indices
                    if (addStart != indexMap.size) {
                        val iterator = indexMap.iterator()
                        for (entry in iterator) {
                            val (index, listener) = entry.value
                            if (index >= addStart) {
                                // Shift indices
                                entry.setValue(Pair(index + addRange, listener))
                            }
                        }
                    }
                    c.addedSubList.forEachIndexed { sublistIndex, observableValue ->
                        val wrapped = WrappedObservableValue(observableValue)
                        indexMap[wrapped] = Pair(addStart + sublistIndex, createListener(wrapped))
                    }
                    nextAdd(addStart, addEnd)
                }
            }
        }
        endChange()
        require(sourceList.size == indexMap.size)
    }

    override fun get(index: Int): A = sourceList[index].value

    override fun getSourceIndex(index: Int) = index

    override val size: Int get() = sourceList.size
}
