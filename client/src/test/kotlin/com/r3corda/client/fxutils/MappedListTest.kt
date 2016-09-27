package com.r3corda.client.fxutils

import javafx.collections.FXCollections
import org.junit.Before
import org.junit.Test

class MappedListTest {

    var sourceList = FXCollections.observableArrayList("Alice")
    var mappedList = MappedList(sourceList) { it.length }
    var replayedList = ReplayedList(mappedList)

    @Before
    fun setup() {
        sourceList = FXCollections.observableArrayList("Alice")
        mappedList = MappedList(sourceList) { it.length }
        replayedList = ReplayedList(mappedList)
    }

    @Test
    fun addWorks() {
        require(replayedList.size == 1)
        require(replayedList[0] == 5)

        sourceList.add("Bob")
        require(replayedList.size == 2)
        require(replayedList[0] == 5)
        require(replayedList[1] == 3)

        sourceList.add(0, "Charlie")
        require(replayedList.size == 3)
        require(replayedList[0] == 7)
        require(replayedList[1] == 5)
        require(replayedList[2] == 3)

    }

    @Test
    fun removeWorks() {
        sourceList.add("Bob")
        sourceList.add(0, "Charlie")
        require(replayedList.size == 3)

        sourceList.removeAt(1)
        require(replayedList.size == 2)
        require(replayedList[0] == 7)
        require(replayedList[1] == 3)
    }

    @Test
    fun permuteWorks() {
        sourceList.add("Bob")
        sourceList.add(0, "Charlie")

        sourceList.sortBy { it.length }

        require(sourceList[0] == "Bob")
        require(sourceList[1] == "Alice")
        require(sourceList[2] == "Charlie")

        require(replayedList.size == 3)
        require(replayedList[0] == 3)
        require(replayedList[1] == 5)
        require(replayedList[2] == 7)
    }
}
