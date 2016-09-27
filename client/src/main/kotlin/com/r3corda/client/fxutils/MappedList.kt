package com.r3corda.client.fxutils

import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.collections.transformation.TransformationList
import java.util.*


/**
 * This is a variant of [EasyBind.map] where the mapped list is backed, therefore the mapping function will only be run
 * when an element is inserted or updated.
 */
class MappedList<A, B>(list: ObservableList<A>, val function: (A) -> B) : TransformationList<B, A>(list) {
    private val backingList = ArrayList<B>(list.size)

    init {
        list.forEach {
            backingList.add(function(it))
        }
    }

    override fun sourceChanged(change: ListChangeListener.Change<out A>) {
        beginChange()
        while (change.next()) {
            if (change.wasPermutated()) {
                val from = change.from
                val to = change.to
                val permutation = IntArray(to, { change.getPermutation(it) })
                val permutedSubList = ArrayList<B?>(to - from)
                permutedSubList.addAll(Collections.nCopies(to - from, null))
                for (i in 0 .. (to - from - 1)) {
                    permutedSubList[permutation[from + i]] = backingList[from + i]
                }
                permutedSubList.forEachIndexed { i, element ->
                    backingList[from + i] = element!!
                }
                nextPermutation(from, to, permutation)
            } else if (change.wasUpdated()) {
                backingList[change.from] = function(source[change.from])
                nextUpdate(change.from)
            } else {
                if (change.wasRemoved()) {
                    val removePosition = change.from
                    val removed = ArrayList<B>(change.removedSize)
                    for (i in 0 .. change.removedSize - 1) {
                        removed.add(backingList.removeAt(removePosition))
                    }
                    nextRemove(change.from, removed)
                }
                if (change.wasAdded()) {
                    val addStart = change.from
                    val addEnd = change.to
                    for (i in addStart .. addEnd - 1) {
                        backingList.add(i, function(change.list[i]))
                    }
                    nextAdd(addStart, addEnd)
                }
            }
        }
        endChange()
    }

    override fun get(index: Int) = backingList[index]
    override val size: Int get() = backingList.size
    override fun getSourceIndex(index: Int) = index
}
