package com.r3corda.client.fxutils

import javafx.beans.InvalidationListener
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
     */
    val indexMap = HashMap<ObservableValue<out A>, Pair<Int, InvalidationListener>>()
    init {
        sourceList.forEachIndexed { index, observableValue ->
            indexMap[observableValue] = Pair(index, createListener(observableValue))
        }
    }

    private fun createListener(observableValue: ObservableValue<out A>): InvalidationListener {
        return InvalidationListener {
            val currentIndex = indexMap[observableValue]!!.first
            beginChange()
            nextAdd(currentIndex, currentIndex + 1)
            endChange()
        }
    }

    override fun sourceChanged(c: ListChangeListener.Change<out ObservableValue<out A>>) {
        beginChange()
        while (c.next()) {
            if (c.wasPermutated()) {
                val from = c.from
                val to = c.to
                val permutation = IntArray(to, { c.getPermutation(it) })
                indexMap.replaceAll { _observableValue, pair -> Pair(permutation[pair.first], pair.second) }
                nextPermutation(from, to, permutation)
            } else if (c.wasUpdated()) {
                throw UnsupportedOperationException("FlattenedList doesn't support Update changes")
            } else {
                val removed = c.removed
                if (removed.size != 0) {
                    val removeStart = indexMap[removed.first()]!!.first
                    val removeEnd = indexMap[removed.last()]!!.first + 1
                    require(removeStart < removeEnd)
                    val removeRange = removeEnd - removeStart
                    val iterator = indexMap.iterator()
                    for (entry in iterator) {
                        val (observableValue, pair) = entry
                        val (index, listener) = pair
                        if (index >= removeStart) {
                            if (index < removeEnd) {
                                observableValue.removeListener(listener)
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
                        indexMap[observableValue] = Pair(addStart + sublistIndex, createListener(observableValue))
                    }
                    nextAdd(addStart, addEnd)
                }
            }
        }
        endChange()
        require(sourceList.size == indexMap.size)
    }

    override fun get(index: Int) = sourceList.get(index).value

    override fun getSourceIndex(index: Int) = index

    override val size: Int get() = sourceList.size
}
