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
import kotlin.test.assertEquals

class MappedListTest {

    lateinit var sourceList: ObservableList<String>
    lateinit var mappedList: ObservableList<Int>
    lateinit var replayedList: ObservableList<Int>

    @Before
    fun setup() {
        sourceList = FXCollections.observableArrayList("Alice")
        mappedList = MappedList(sourceList) { it.length }
        replayedList = ReplayedList(mappedList)
    }

    @Test
    fun addWorks() {
        assertEquals(replayedList.size, 1)
        assertEquals(replayedList[0], 5)

        sourceList.add("Bob")
        assertEquals(replayedList.size, 2)
        assertEquals(replayedList[0], 5)
        assertEquals(replayedList[1], 3)

        sourceList.add(0, "Charlie")
        assertEquals(replayedList.size, 3)
        assertEquals(replayedList[0], 7)
        assertEquals(replayedList[1], 5)
        assertEquals(replayedList[2], 3)

    }

    @Test
    fun removeWorks() {
        sourceList.add("Bob")
        sourceList.add(0, "Charlie")
        assertEquals(replayedList.size, 3)

        sourceList.removeAt(1)
        assertEquals(replayedList.size, 2)
        assertEquals(replayedList[0], 7)
        assertEquals(replayedList[1], 3)
    }

    @Test
    fun permuteWorks() {
        sourceList.add("Bob")
        sourceList.add(0, "Charlie")

        sourceList.sortBy { it.length }

        assertEquals(sourceList[0], "Bob")
        assertEquals(sourceList[1], "Alice")
        assertEquals(sourceList[2], "Charlie")

        assertEquals(replayedList.size, 3)
        assertEquals(replayedList[0], 3)
        assertEquals(replayedList[1], 5)
        assertEquals(replayedList[2], 7)
    }
}
