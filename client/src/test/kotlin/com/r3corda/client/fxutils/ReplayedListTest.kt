package com.r3corda.client.fxutils

import javafx.collections.FXCollections
import org.junit.Before
import org.junit.Test

class ReplayedListTest {

    var sourceList = FXCollections.observableArrayList(1234)
    var replayedList = ReplayedList(sourceList)

    @Before
    fun setup() {
        sourceList = FXCollections.observableArrayList(1234)
        replayedList = ReplayedList(sourceList)
    }

    @Test
    fun addWorks() {
        require(replayedList.size == 1)
        require(replayedList[0] == 1234)

        sourceList.add(12)
        require(replayedList.size == 2)
        require(replayedList[0] == 1234)
        require(replayedList[1] == 12)

        sourceList.add(34)
        require(replayedList.size == 3)
        require(replayedList[0] == 1234)
        require(replayedList[1] == 12)
        require(replayedList[2] == 34)

        sourceList.add(0, 56)
        require(replayedList.size == 4)
        require(replayedList[0] == 56)
        require(replayedList[1] == 1234)
        require(replayedList[2] == 12)
        require(replayedList[3] == 34)

        sourceList.addAll(2, listOf(78, 910))
        require(replayedList.size == 6)
        require(replayedList[0] == 56)
        require(replayedList[1] == 1234)
        require(replayedList[2] == 78)
        require(replayedList[3] == 910)
        require(replayedList[4] == 12)
        require(replayedList[5] == 34)
    }

    @Test
    fun removeWorks() {
        val firstRemoved = sourceList.removeAt(0)
        require(firstRemoved == 1234)
        require(replayedList.size == 0)

        sourceList.add(12)
        sourceList.add(34)
        sourceList.add(56)
        require(replayedList.size == 3)
        val secondRemoved = sourceList.removeAt(1)
        require(secondRemoved == 34)
        require(replayedList.size == 2)
        require(replayedList[0] == 12)
        require(replayedList[1] == 56)

        sourceList.clear()
        require(replayedList.size == 0)
    }

    @Test
    fun updateWorks() {
        require(replayedList[0] == 1234)
        sourceList[0] = 4321
        require(replayedList[0] == 4321)

        sourceList.add(0, 12)
        sourceList[1] = 8765
        require(replayedList[0] == 12)
        require(replayedList[1] == 8765)

        sourceList[0] = 34
        require(replayedList[0] == 34)
        require(replayedList[1] == 8765)
    }
}
