package net.corda.client.jfx.utils

import co.paralleluniverse.common.util.VisibleForTesting
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.collections.transformation.TransformationList
import java.util.*

/**
 * [ConcatenatedList] takes a list of lists and concatenates them. Any change to the underlying lists or the outer list
 * is propagated as expected.
 */
class ConcatenatedList<A>(sourceList: ObservableList<ObservableList<A>>) : TransformationList<A, ObservableList<A>>(sourceList) {
    // A wrapper for input lists so we hash differently even if a list is reused in the input.
    @VisibleForTesting
    internal class WrappedObservableList<A>(
            val observableList: ObservableList<A>
    )
    // First let's clarify some concepts as it's easy to confuse which list we're handling where.
    // Throughout the commentary and the code we will refer to the lists contained in the source list as "nested lists",
    // their elements "nested elements", whereas the containing list will be called "source list", its elements being
    // the nested lists. We will refer to the final concatenated list as "result list".

    // We maintain two bookkeeping data-structures.
    // 'indexMap' stores a mapping from nested lists to their respective source list indices and listeners.
    // 'nestedIndexOffsets' stores for each nested list the index of the *next* nested element in the result list.
    // We also have a helper function 'startingOffsetOf', which given an index of a nested list in the source list
    // returns the index of its first element in the result list, or where it would be if it had one.
    // For example:
    //   nested lists = { {"a", "b"}, {"c"}, {} }
    //   result list = { "a", "b", "c" }
    //   indexMap = [ {"c"} -> (1, listener),
    //                {} -> (2, listener),
    //                {"a", "b"} -> (0, listener) ]
    //   nestedIndexOffsets = { 2, 3, 3 }
    //   startingOffsetOf = { 0, 2, 3 }
    // Note that similar to 'nestedIndexOffsets', 'startingOffsetOf' also isn't a one-to-one mapping because of
    // potentially several empty nested lists.
    @VisibleForTesting
    internal val indexMap = HashMap<WrappedObservableList<out A>, Pair<Int, ListChangeListener<A>>>()
    @VisibleForTesting
    internal val nestedIndexOffsets = ArrayList<Int>(sourceList.size)

    init {
        var offset = 0
        sourceList.forEachIndexed { index, observableList ->
            val wrapped = WrappedObservableList(observableList)
            indexMap[wrapped] = Pair(index, createListener(wrapped))
            offset += observableList.size
            nestedIndexOffsets.add(offset)
        }
    }

    private fun startingOffsetOf(listIndex: Int): Int {
        return if (listIndex == 0) {
            0
        } else {
            nestedIndexOffsets[listIndex - 1]
        }
    }

    // This is where we create a listener for a *nested* list. Note that 'indexMap' doesn't need to be adjusted on any
    // of these changes as the indices of nested lists don't change, just their contents.
    private fun createListener(wrapped: WrappedObservableList<A>): ListChangeListener<A> {
        val listener = ListChangeListener<A> { change ->
            beginChange()
            while (change.next()) {
                if (change.wasPermutated()) {
                    // If a nested list is permuted we simply offset the permutation by the startingOffsetOf the list.
                    // Note that we don't need to invalidate offsets.
                    val nestedListIndex = indexMap[wrapped]!!.first
                    val permutation = IntArray(change.to)
                    val startingOffset = startingOffsetOf(nestedListIndex)
                    // firstTouched is the result list index of the beginning of the permutation.
                    val firstTouched = startingOffset + change.from
                    // We first set the non-permuted indices.
                    for (i in 0 until firstTouched) {
                        permutation[i] = i
                    }
                    // Then the permuted ones.
                    for (i in firstTouched until startingOffset + change.to) {
                        permutation[startingOffset + i] = change.getPermutation(i)
                    }
                    nextPermutation(firstTouched, startingOffset + change.to, permutation)
                } else if (change.wasUpdated()) {
                    // If a nested element is updated we simply propagate the update by offsetting the nested element index
                    // by the startingOffsetOf the nested list.
                    val listIndex = indexMap[wrapped]!!.first
                    val startingOffset = startingOffsetOf(listIndex)
                    for (i in change.from until change.to) {
                        nextUpdate(startingOffset + i)
                    }
                } else {
                    if (change.wasRemoved()) {
                        // If nested elements are removed we again simply offset the change. We also need to invalidate
                        // 'nestedIndexOffsets' unless we removed the same number of elements as we added
                        val listIndex = indexMap[wrapped]!!.first
                        if (!(change.wasAdded() && change.addedSize == change.removedSize)) {
                            invalidateOffsets(listIndex)
                        }
                        val startingOffset = startingOffsetOf(listIndex)
                        nextRemove(startingOffset + change.from, change.removed)
                    }
                    if (change.wasAdded()) {
                        // Similar logic to remove.
                        val listIndex = indexMap[wrapped]!!.first
                        if (!(change.wasRemoved() && change.addedSize == change.removedSize)) {
                            invalidateOffsets(listIndex)
                        }
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

    // This is where we handle changes to the *source* list.
    override fun sourceChanged(change: ListChangeListener.Change<out ObservableList<A>>) {
        beginChange()
        while (change.next()) {
            if (change.wasPermutated()) {
                // If the source list was permuted we adjust 'nestedIndexOffsets' and translate the permutation to apply
                // to the nested elements.
                // For example:
                //   original list:          { {"a", "b"}, {"c", "d"}, {} }
                //   original permutation:   { 2, 1, 0 }
                //   permuted list:          { {}, {"c", "d"}, {"a", "b"} }
                //   translated permutation: { 2, 3, 0, 1 }

                // First we apply the permutation to the 'indexMap'
                val iterator = indexMap.iterator()
                for (entry in iterator) {
                    val (index, listener) = entry.value
                    if (index >= change.from && index < change.to) {
                        entry.setValue(Pair(change.getPermutation(index), listener))
                    }
                }
                // We apply the permutation to the relevant part of 'nestedIndexOffsets'.
                val newSubNestedIndexOffsets = IntArray(change.to - change.from)
                val firstTouched = if (change.from == 0) 0 else nestedIndexOffsets[change.from - 1]
                var currentOffset = firstTouched
                for (i in 0 until change.to - change.from) {
                    currentOffset += source[change.from + i].size
                    newSubNestedIndexOffsets[i] = currentOffset
                }
                // Now we create the permutation array for the result list.
                val concatenatedPermutation = IntArray(newSubNestedIndexOffsets.last())
                // Set the non-permuted part
                var offset = 0
                for (i in 0 until change.from) {
                    val nestedList = source[i]
                    for (j in offset until offset + nestedList.size) {
                        concatenatedPermutation[j] = j
                    }
                    offset += nestedList.size
                }
                // Now the permuted part
                for (i in 0 until newSubNestedIndexOffsets.size) {
                    val startingOffset = startingOffsetOf(change.from + i)
                    val permutedListIndex = change.getPermutation(change.from + i)
                    val permutedOffset = (if (permutedListIndex == 0) 0 else newSubNestedIndexOffsets[permutedListIndex - 1])
                    for (j in 0 until source[permutedListIndex].size) {
                        concatenatedPermutation[startingOffset + j] = permutedOffset + j
                    }
                }
                // Record permuted offsets
                for (i in 0 until newSubNestedIndexOffsets.size) {
                    nestedIndexOffsets[change.from + i] = newSubNestedIndexOffsets[i]
                }
                nextPermutation(firstTouched, newSubNestedIndexOffsets.last(), concatenatedPermutation)
            } else if (change.wasUpdated()) {
                // This would be translated to remove + add, but that requires a backing list for removed elements
                throw UnsupportedOperationException("Updates not supported")
            } else {
                if (change.wasRemoved()) {
                    // If nested lists were removed we iterate over 'indexMap' and adjust the indices accordingly,
                    // remove listeners and remove relevant mappings as well. We also invalidate nested offsets.
                    val iterator = indexMap.iterator()
                    for (entry in iterator) {
                        val (wrapped, pair) = entry
                        val (index, listener) = pair
                        if (index >= change.from) {
                            val removeEnd = change.from + change.removedSize
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
                    // We recalculate offsets early as we need the range anyway.
                    recalculateOffsets()
                    nextAdd(startingOffsetOf(change.from), nestedIndexOffsets[change.to - 1])
                }
            }
            recalculateOffsets()
        }
        endChange()
    }

    // Tracks the first position where the *nested* offset is invalid
    private var firstInvalidatedPosition = sourceList.size

    private fun invalidateOffsets(index: Int) {
        firstInvalidatedPosition = Math.min(firstInvalidatedPosition, index)
    }

    private fun recalculateOffsets() {
        if (firstInvalidatedPosition < source.size) {
            val firstInvalid = firstInvalidatedPosition
            var offset = if (firstInvalid == 0) 0 else nestedIndexOffsets[firstInvalid - 1]
            for (i in firstInvalid until source.size) {
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

    override val size: Int
        get() {
            recalculateOffsets()
            return if (nestedIndexOffsets.size > 0) {
                nestedIndexOffsets.last()
            } else {
                0
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

        return if (listIndex >= 0) {
            var nonEmptyListIndex = listIndex + 1
            while (source[nonEmptyListIndex].isEmpty()) {
                nonEmptyListIndex++
            }
            source[nonEmptyListIndex][0]
        } else {
            // The element is in the range of this list
            val rangeListIndex = -listIndex - 1
            val subListOffset = index - startingOffsetOf(rangeListIndex)
            source[rangeListIndex][subListOffset]
        }
    }

}
