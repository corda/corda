package com.r3corda.client.fxutils

import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.collections.transformation.TransformationList
import java.util.*
import kotlin.comparisons.compareValues

class ConcatenatedList<A>(sourceList: ObservableList<ObservableList<A>>) : TransformationList<A, ObservableList<A>>(sourceList) {
    class WrappedObservableList<A>(
            val observableList: ObservableList<A>
    )
    private val indexMap = HashMap<WrappedObservableList<out A>, Pair<Int, ListChangeListener<A>>>()
    // Maps each list index to the offset of the next nested element
    // Example: { {"a", "b"}, {"c"} } -> { 2, 3 }
    private val nestedIndexOffsets = ArrayList<Int>(sourceList.size)
    init {
        var offset = 0
        sourceList.forEachIndexed { index, observableList ->
            val wrapped = WrappedObservableList(observableList)
            indexMap[wrapped] = Pair(index, createListener(wrapped))
            offset += observableList.size
            nestedIndexOffsets.add(offset)
        }
    }

    private fun createListener(wrapped: WrappedObservableList<A>): ListChangeListener<A> {
        val listener = ListChangeListener<A> { change ->
            beginChange()
            while (change.next()) {
                if (change.wasPermutated()) {
                    val listIndex = indexMap[wrapped]!!.first
                    val permutation = IntArray(change.to)
                    if (listIndex >= firstInvalidatedPosition) {
                        recalculateOffsets()
                    }
                    val startingOffset = startingOffsetOf(listIndex)
                    val firstTouched = startingOffset + change.from
                    for (i in 0..firstTouched - 1) {
                        permutation[i] = i
                    }
                    for (i in startingOffset + change.from..startingOffset + change.to - 1) {
                        permutation[startingOffset + i] = change.getPermutation(i)
                    }
                    nextPermutation(firstTouched, startingOffset + change.to, permutation)
                } else if (change.wasUpdated()) {
                    val listIndex = indexMap[wrapped]!!.first
                    val startingOffset = startingOffsetOf(listIndex)
                    for (i in change.from..change.to - 1) {
                        nextUpdate(startingOffset + i)
                    }
                } else {
                    if (change.wasRemoved()) {
                        val listIndex = indexMap[wrapped]!!.first
                        invalidateOffsets(listIndex)
                        val startingOffset = startingOffsetOf(listIndex)
                        nextRemove(startingOffset + change.from, change.removed)
                    }
                    if (change.wasAdded()) {
                        val listIndex = indexMap[wrapped]!!.first
                        invalidateOffsets(listIndex)
                        val startingOffset = startingOffsetOf(listIndex)
                        nextAdd(startingOffset + change.from, startingOffset + change.to)
                    }
                }
                recalculateOffsets()
            }
            endChange()
        }
        wrapped.observableList.addListener(listener)
        return listener
    }

    // Tracks the first position where the *nested* offset is invalid
    private var firstInvalidatedPosition = sourceList.size

    override fun sourceChanged(change: ListChangeListener.Change<out ObservableList<A>>) {
        beginChange()
        while (change.next()) {
            if (change.wasPermutated()) {
                // Update indexMap
                val iterator = indexMap.iterator()
                for (entry in iterator) {
                    val (wrapped, pair) = entry
                    val (index, listener) = pair
                    if (index >= change.from && index < change.to) {
                        entry.setValue(Pair(change.getPermutation(index), listener))
                    }
                }
                // Calculate the permuted sublist of nestedIndexOffsets
                val newSubNestedIndexOffsets = IntArray(change.to - change.from)
                val firstTouched = if (change.from == 0) 0 else nestedIndexOffsets[change.from - 1]
                var currentOffset = firstTouched
                for (i in 0 .. change.to - change.from - 1) {
                    currentOffset += source[change.from + i].size
                    newSubNestedIndexOffsets[i] = currentOffset
                }
                val concatenatedPermutation = IntArray(newSubNestedIndexOffsets.last())
                // Set the non-permuted part
                var offset = 0
                for (i in 0 .. change.from - 1) {
                    val nestedList = source[i]
                    for (j in offset .. offset + nestedList.size - 1) {
                        concatenatedPermutation[j] = j
                    }
                    offset += nestedList.size
                }
                // Now the permuted part
                for (i in 0 .. newSubNestedIndexOffsets.size - 1) {
                    val startingOffset = startingOffsetOf(change.from + i)
                    val permutedListIndex = change.getPermutation(change.from + i)
                    val permutedOffset = (if (permutedListIndex == 0) 0 else newSubNestedIndexOffsets[permutedListIndex - 1])
                    for (j in 0 .. source[permutedListIndex].size - 1) {
                        concatenatedPermutation[startingOffset + j] = permutedOffset + j
                    }
                }
                // Record permuted offsets
                for (i in 0 .. newSubNestedIndexOffsets.size - 1) {
                    nestedIndexOffsets[change.from + i] = newSubNestedIndexOffsets[i]
                }
                nextPermutation(firstTouched, newSubNestedIndexOffsets.last(), concatenatedPermutation)
            } else if (change.wasUpdated()) {
                // This would be translated to remove + add, but that requires a backing list for removed elements
                throw UnsupportedOperationException("Updates not supported")
            } else {
                if (change.wasRemoved()) {
                    // Update indexMap
                    val iterator = indexMap.iterator()
                    for (entry in iterator) {
                        val (wrapped, pair) = entry
                        val (index, listener) = pair
                        val removeEnd = change.from + change.removedSize
                        if (index >= change.from) {
                            if (index < removeEnd) {
                                wrapped.observableList.removeListener(listener)
                                iterator.remove()
                            } else {
                                entry.setValue(Pair(index - change.removedSize, listener))
                            }
                        }
                    }
                    // Propagate changes
                    invalidateOffsets(change.from)
                    val removeStart = startingOffsetOf(change.from)
                    val removed = change.removed.flatMap { it }
                    nextRemove(removeStart, removed)
                }
                if (change.wasAdded()) {
                    // Update indexMap
                    if (change.from != indexMap.size) {
                        val iterator = indexMap.iterator()
                        for (entry in iterator) {
                            val (index, listener) = entry.value
                            if (index >= change.from) {
                                // Shift indices
                                entry.setValue(Pair(index + change.addedSize, listener))
                            }
                        }
                    }
                    change.addedSubList.forEachIndexed { sublistIndex, observableList ->
                        val wrapped = WrappedObservableList(observableList)
                        indexMap[wrapped] = Pair(change.from + sublistIndex, createListener(wrapped))
                    }
                    invalidateOffsets(change.from)
                    recalculateOffsets()
                    nextAdd(startingOffsetOf(change.from), nestedIndexOffsets[change.to - 1])
                    for (i in change.from .. change.to - 1) {
                        source[i].addListener { change: ListChangeListener.Change<out A> ->

                        }
                    }
                }
            }
            recalculateOffsets()
        }
        endChange()
    }

    private fun invalidateOffsets(index: Int) {
        firstInvalidatedPosition = Math.min(firstInvalidatedPosition, index)
    }

    private fun startingOffsetOf(listIndex: Int): Int {
        if (listIndex == 0) {
            return 0
        } else {
            return nestedIndexOffsets[listIndex - 1]
        }
    }

    private fun recalculateOffsets() {
        if (firstInvalidatedPosition < source.size) {
            val firstInvalid = firstInvalidatedPosition
            var offset = if (firstInvalid == 0) 0 else nestedIndexOffsets[firstInvalid - 1]
            for (i in firstInvalid .. source.size - 1) {
                offset += source[i].size
                if (i < nestedIndexOffsets.size) {
                    nestedIndexOffsets[i] = offset
                } else {
                    nestedIndexOffsets.add(offset)
                }
            }
            while (nestedIndexOffsets.size > source.size) {
                nestedIndexOffsets.removeAt(source.size)
            }
            firstInvalidatedPosition = nestedIndexOffsets.size
        }
    }

    override val size: Int get() {
        recalculateOffsets()
        if (nestedIndexOffsets.size > 0) {
            return nestedIndexOffsets.last()
        } else {
            return 0
        }
    }

    override fun getSourceIndex(index: Int): Int {
        throw UnsupportedOperationException("Source index not supported in concatenation")
    }

    override fun get(index: Int): A {
        recalculateOffsets()
        val listIndex = nestedIndexOffsets.binarySearch(
                comparison = { offset -> compareValues(offset, index) }
        )

        if (listIndex >= 0) {
            var nonEmptyListIndex = listIndex + 1
            while (source[nonEmptyListIndex].isEmpty()) {
                nonEmptyListIndex++
            }
            return source[nonEmptyListIndex][0]
        } else {
            // The element is in the range of this list
            val rangeListIndex = -listIndex - 1
            val subListOffset = index - startingOffsetOf(rangeListIndex)
            return source[rangeListIndex][subListOffset]
        }
    }

}
