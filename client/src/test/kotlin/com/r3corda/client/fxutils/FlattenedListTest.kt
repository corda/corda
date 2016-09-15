package com.r3corda.client.fxutils

import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import org.junit.Before
import org.junit.Test

class FlattenedListTest {

    var sourceList = FXCollections.observableArrayList(SimpleObjectProperty(1234))
    var flattenedList = FlattenedList(sourceList)

    @Before
    fun setup() {
        sourceList = FXCollections.observableArrayList(SimpleObjectProperty(1234))
        flattenedList = FlattenedList(sourceList)
    }

    @Test
    fun addWorks() {
        require(flattenedList.size == 1)
        require(flattenedList[0] == 1234)

        sourceList.add(SimpleObjectProperty(12))
        require(flattenedList.size == 2)
        require(flattenedList[0] == 1234)
        require(flattenedList[1] == 12)

        sourceList.add(SimpleObjectProperty(34))
        require(flattenedList.size == 3)
        require(flattenedList[0] == 1234)
        require(flattenedList[1] == 12)
        require(flattenedList[2] == 34)

        sourceList.add(0, SimpleObjectProperty(56))
        require(flattenedList.size == 4)
        require(flattenedList[0] == 56)
        require(flattenedList[1] == 1234)
        require(flattenedList[2] == 12)
        require(flattenedList[3] == 34)

        sourceList.addAll(2, listOf(SimpleObjectProperty(78), SimpleObjectProperty(910)))
        require(flattenedList.size == 6)
        require(flattenedList[0] == 56)
        require(flattenedList[1] == 1234)
        require(flattenedList[2] == 78)
        require(flattenedList[3] == 910)
        require(flattenedList[4] == 12)
        require(flattenedList[5] == 34)
    }

    @Test
    fun removeWorks() {
        val firstRemoved = sourceList.removeAt(0)
        require(firstRemoved.get() == 1234)
        require(flattenedList.size == 0)
        firstRemoved.set(123)

        sourceList.add(SimpleObjectProperty(12))
        sourceList.add(SimpleObjectProperty(34))
        sourceList.add(SimpleObjectProperty(56))
        require(flattenedList.size == 3)
        val secondRemoved = sourceList.removeAt(1)
        require(secondRemoved.get() == 34)
        require(flattenedList.size == 2)
        require(flattenedList[0] == 12)
        require(flattenedList[1] == 56)
        secondRemoved.set(123)

        sourceList.clear()
        require(flattenedList.size == 0)
    }

    @Test
    fun updatingObservableWorks() {
        require(flattenedList[0] == 1234)
        sourceList[0].set(4321)
        require(flattenedList[0] == 4321)

        sourceList.add(0, SimpleObjectProperty(12))
        sourceList[1].set(8765)
        require(flattenedList[0] == 12)
        require(flattenedList[1] == 8765)

        sourceList[0].set(34)
        require(flattenedList[0] == 34)
        require(flattenedList[1] == 8765)
    }
}
