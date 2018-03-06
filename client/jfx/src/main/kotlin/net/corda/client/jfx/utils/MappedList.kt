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

import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.collections.transformation.TransformationList
import java.util.*


/**
 * This is a variant of [EasyBind.map] where the mapped list is backed, therefore the mapping function will only be run
 * when an element is inserted or updated.
 * Use this instead of [EasyBind.map] to trade off memory vs CPU, or if (god forbid) the mapped function is side-effecting.
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
                // Note how we don't re-run the mapping function on a permutation. If we supported mapIndexed we would
                // have to.
                val from = change.from
                val to = change.to
                val permutation = IntArray(to) { change.getPermutation(it) }
                val permutedSubList = ArrayList<B?>(to - from)
                permutedSubList.addAll(Collections.nCopies(to - from, null))
                for (i in 0.until(to - from)) {
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
                    for (i in 0.until(change.removedSize)) {
                        removed.add(backingList.removeAt(removePosition))
                    }
                    nextRemove(change.from, removed)
                }
                if (change.wasAdded()) {
                    val addStart = change.from
                    val addEnd = change.to
                    for (i in addStart.until(addEnd)) {
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
