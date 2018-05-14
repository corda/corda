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
import javafx.collections.ObservableList
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class ConcatenatedListTest {

    lateinit var sourceList: ObservableList<ObservableList<String>>
    lateinit var concatenatedList: ConcatenatedList<String>
    lateinit var replayedList: ObservableList<String>

    @Before
    fun setup() {
        sourceList = FXCollections.observableArrayList<ObservableList<String>>(FXCollections.observableArrayList("hello"))
        concatenatedList = ConcatenatedList(sourceList)
        replayedList = ReplayedList(concatenatedList)
    }

    // A helper function for tests that checks internal invariants.
    fun <A> ConcatenatedList<A>.checkInvariants() {
        assertEquals(nestedIndexOffsets.size, source.size)
        var currentOffset = 0
        for (i in 0 until source.size) {
            currentOffset += source[i].size
            assertEquals(nestedIndexOffsets[i], currentOffset)
        }

        assertEquals(indexMap.size, source.size)
        for ((wrapped, pair) in indexMap) {
            val index = pair.first
            val foundListIndices = ArrayList<Int>()
            source.forEachIndexed { i, list ->
                if (wrapped.observableList == list) {
                    foundListIndices.add(i)
                }
            }
            require(foundListIndices.any { it == index })
        }
    }

    @Test
    fun addWorks() {
        concatenatedList.checkInvariants()
        assertEquals(replayedList.size, 1)
        assertEquals(replayedList[0], "hello")

        sourceList.add(FXCollections.observableArrayList("a", "b"))
        concatenatedList.checkInvariants()
        assertEquals(replayedList.size, 3)
        assertEquals(replayedList[0], "hello")
        assertEquals(replayedList[1], "a")
        assertEquals(replayedList[2], "b")

        sourceList.add(1, FXCollections.observableArrayList("c"))
        concatenatedList.checkInvariants()
        assertEquals(replayedList.size, 4)
        assertEquals(replayedList[0], "hello")
        assertEquals(replayedList[1], "c")
        assertEquals(replayedList[2], "a")
        assertEquals(replayedList[3], "b")

        sourceList[0].addAll("d", "e")
        concatenatedList.checkInvariants()
        assertEquals(replayedList.size, 6)
        assertEquals(replayedList[0], "hello")
        assertEquals(replayedList[1], "d")
        assertEquals(replayedList[2], "e")
        assertEquals(replayedList[3], "c")
        assertEquals(replayedList[4], "a")
        assertEquals(replayedList[5], "b")

        sourceList[1].add(0, "f")
        concatenatedList.checkInvariants()
        assertEquals(replayedList.size, 7)
        assertEquals(replayedList[0], "hello")
        assertEquals(replayedList[1], "d")
        assertEquals(replayedList[2], "e")
        assertEquals(replayedList[3], "f")
        assertEquals(replayedList[4], "c")
        assertEquals(replayedList[5], "a")
        assertEquals(replayedList[6], "b")
    }

    @Test
    fun removeWorks() {
        sourceList.add(FXCollections.observableArrayList("a", "b"))
        sourceList.add(1, FXCollections.observableArrayList("c"))
        sourceList[0].addAll("d", "e")
        sourceList[1].add(0, "f")

        sourceList.removeAt(1)
        concatenatedList.checkInvariants()
        assertEquals(replayedList.size, 5)
        assertEquals(replayedList[0], "hello")
        assertEquals(replayedList[1], "d")
        assertEquals(replayedList[2], "e")
        assertEquals(replayedList[3], "a")
        assertEquals(replayedList[4], "b")

        sourceList[0].clear()
        concatenatedList.checkInvariants()
        assertEquals(replayedList.size, 2)
        assertEquals(replayedList[0], "a")
        assertEquals(replayedList[1], "b")

        sourceList[1].removeAt(0)
        concatenatedList.checkInvariants()
        assertEquals(replayedList.size, 1)
        assertEquals(replayedList[0], "b")
    }

    @Test
    fun permutationWorks() {
        sourceList.addAll(FXCollections.observableArrayList("a", "b"), FXCollections.observableArrayList("c"))
        concatenatedList.checkInvariants()
        assertEquals(replayedList.size, 4)
        assertEquals(replayedList[0], "hello")
        assertEquals(replayedList[1], "a")
        assertEquals(replayedList[2], "b")
        assertEquals(replayedList[3], "c")

        sourceList.sortWith(Comparator<ObservableList<String>> { p0, p1 -> p0.size - p1.size })
        concatenatedList.checkInvariants()
        assertEquals(replayedList.size, 4)
        assertEquals(replayedList[0], "hello")
        assertEquals(replayedList[1], "c")
        assertEquals(replayedList[2], "a")
        assertEquals(replayedList[3], "b")

        sourceList.add(0, FXCollections.observableArrayList("d", "e", "f"))
        sourceList.sortWith(Comparator<ObservableList<String>> { p0, p1 -> p0.size - p1.size })
        concatenatedList.checkInvariants()
        assertEquals(replayedList.size, 7)
        assertEquals(replayedList[0], "hello")
        assertEquals(replayedList[1], "c")
        assertEquals(replayedList[2], "a")
        assertEquals(replayedList[3], "b")
        assertEquals(replayedList[4], "d")
        assertEquals(replayedList[5], "e")
        assertEquals(replayedList[6], "f")
    }

}
