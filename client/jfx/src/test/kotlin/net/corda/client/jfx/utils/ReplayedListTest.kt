package net.corda.client.jfx.utils

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class ReplayedListTest {

    var sourceList: ObservableList<Int> = FXCollections.observableArrayList(1234)
    var replayedList = ReplayedList(sourceList)

    @Before
    fun setup() {
        sourceList = FXCollections.observableArrayList(1234)
        replayedList = ReplayedList(sourceList)
    }

    @Test
    fun addWorks() {
        assertEquals(replayedList.size, 1)
        assertEquals(replayedList[0], 1234)

        sourceList.add(12)
        assertEquals(replayedList.size, 2)
        assertEquals(replayedList[0], 1234)
        assertEquals(replayedList[1], 12)

        sourceList.add(34)
        assertEquals(replayedList.size, 3)
        assertEquals(replayedList[0], 1234)
        assertEquals(replayedList[1], 12)
        assertEquals(replayedList[2], 34)

        sourceList.add(0, 56)
        assertEquals(replayedList.size, 4)
        assertEquals(replayedList[0], 56)
        assertEquals(replayedList[1], 1234)
        assertEquals(replayedList[2], 12)
        assertEquals(replayedList[3], 34)

        sourceList.addAll(2, listOf(78, 910))
        assertEquals(replayedList.size, 6)
        assertEquals(replayedList[0], 56)
        assertEquals(replayedList[1], 1234)
        assertEquals(replayedList[2], 78)
        assertEquals(replayedList[3], 910)
        assertEquals(replayedList[4], 12)
        assertEquals(replayedList[5], 34)
    }

    @Test
    fun removeWorks() {
        val firstRemoved = sourceList.removeAt(0)
        assertEquals(firstRemoved, 1234)
        assertEquals(replayedList.size, 0)

        sourceList.add(12)
        sourceList.add(34)
        sourceList.add(56)
        assertEquals(replayedList.size, 3)
        val secondRemoved = sourceList.removeAt(1)
        assertEquals(secondRemoved, 34)
        assertEquals(replayedList.size, 2)
        assertEquals(replayedList[0], 12)
        assertEquals(replayedList[1], 56)

        sourceList.clear()
        assertEquals(replayedList.size, 0)
    }

    @Test
    fun updateWorks() {
        assertEquals(replayedList[0], 1234)
        sourceList[0] = 4321
        assertEquals(replayedList[0], 4321)

        sourceList.add(0, 12)
        sourceList[1] = 8765
        assertEquals(replayedList[0], 12)
        assertEquals(replayedList[1], 8765)

        sourceList[0] = 34
        assertEquals(replayedList[0], 34)
        assertEquals(replayedList[1], 8765)
    }
}
