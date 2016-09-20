package com.r3corda.client.fxutils

import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import org.junit.Before
import org.junit.Test

class FlattenedListTest {

    var sourceList = FXCollections.observableArrayList(SimpleObjectProperty(1234))
    var flattenedList = FlattenedList(sourceList)
    var replayedList = ReplayedList(flattenedList)

    @Before
    fun setup() {
        sourceList = FXCollections.observableArrayList(SimpleObjectProperty(1234))
        flattenedList = FlattenedList(sourceList)
        replayedList = ReplayedList(flattenedList)
    }

    @Test
    fun addWorks() {
        require(replayedList.size == 1)
        require(replayedList[0] == 1234)

        sourceList.add(SimpleObjectProperty(12))
        require(replayedList.size == 2)
        require(replayedList[0] == 1234)
        require(replayedList[1] == 12)

        sourceList.add(SimpleObjectProperty(34))
        require(replayedList.size == 3)
        require(replayedList[0] == 1234)
        require(replayedList[1] == 12)
        require(replayedList[2] == 34)

        sourceList.add(0, SimpleObjectProperty(56))
        require(replayedList.size == 4)
        require(replayedList[0] == 56)
        require(replayedList[1] == 1234)
        require(replayedList[2] == 12)
        require(replayedList[3] == 34)

        sourceList.addAll(2, listOf(SimpleObjectProperty(78), SimpleObjectProperty(910)))
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
        require(firstRemoved.get() == 1234)
        require(replayedList.size == 0)
        firstRemoved.set(123)

        sourceList.add(SimpleObjectProperty(12))
        sourceList.add(SimpleObjectProperty(34))
        sourceList.add(SimpleObjectProperty(56))
        require(replayedList.size == 3)
        val secondRemoved = sourceList.removeAt(1)
        require(secondRemoved.get() == 34)
        require(replayedList.size == 2)
        require(replayedList[0] == 12)
        require(replayedList[1] == 56)
        secondRemoved.set(123)

        sourceList.clear()
        require(replayedList.size == 0)
    }

    @Test
    fun updatingObservableWorks() {
        require(replayedList[0] == 1234)
        sourceList[0].set(4321)
        require(replayedList[0] == 4321)

        sourceList.add(0, SimpleObjectProperty(12))
        sourceList[1].set(8765)
        require(replayedList[0] == 12)
        require(replayedList[1] == 8765)

        sourceList[0].set(34)
        require(replayedList[0] == 34)
        require(replayedList[1] == 8765)
    }

    @Test
    fun reusingObservableWorks() {
        val observable = SimpleObjectProperty(12)
        sourceList.add(observable)
        sourceList.add(observable)
        require(replayedList.size == 3)
        require(replayedList[0] == 1234)
        require(replayedList[1] == 12)
        require(replayedList[2] == 12)

        observable.set(34)
        require(replayedList.size == 3)
        require(replayedList[0] == 1234)
        require(replayedList[1] == 34)
        require(replayedList[2] == 34)

        sourceList.removeAt(1)
        require(replayedList.size == 2)
        require(replayedList[0] == 1234)
        require(replayedList[1] == 34)
    }
}
