package com.r3corda.client.fxutils

import javafx.collections.FXCollections
import org.junit.Before
import org.junit.Test

class AssociatedListTest {

    var sourceList = FXCollections.observableArrayList(0)
    var associatedList = AssociatedList(sourceList, { it % 3 }) { mod3, number -> number }
    var replayedMap = ReplayedMap(associatedList)

    @Before
    fun setup() {
        sourceList = FXCollections.observableArrayList(0)
        associatedList = AssociatedList(sourceList, { it % 3 }) { mod3, number -> number }
        replayedMap = ReplayedMap(associatedList)
    }

    @Test
    fun addWorks() {
        require(replayedMap.size == 1)
        require(replayedMap[0] == 0)

        sourceList.add(2)
        require(replayedMap.size == 2)
        require(replayedMap[0] == 0)
        require(replayedMap[2] == 2)

        sourceList.add(0, 4)
        require(replayedMap.size == 3)
        require(replayedMap[0] == 0)
        require(replayedMap[2] == 2)
        require(replayedMap[1] == 4)
    }

    @Test
    fun removeWorks() {
        sourceList.addAll(2, 4)
        require(replayedMap.size == 3)

        sourceList.removeAt(0)
        require(replayedMap.size == 2)
        require(replayedMap[2] == 2)
        require(replayedMap[1] == 4)

        sourceList.add(1, 12)
        require(replayedMap.size == 3)
        require(replayedMap[2] == 2)
        require(replayedMap[1] == 4)
        require(replayedMap[0] == 12)

        sourceList.clear()
        require(replayedMap.size == 0)
    }

    @Test
    fun updateWorks() {
        sourceList.addAll(2, 4)
        require(replayedMap.size == 3)

        sourceList[1] = 5
        require(replayedMap.size == 3)
        require(replayedMap[0] == 0)
        require(replayedMap[2] == 5)
        require(replayedMap[1] == 4)

        sourceList.removeAt(1)
        require(replayedMap.size == 2)
        require(replayedMap[0] == 0)
        require(replayedMap[1] == 4)
    }
}
