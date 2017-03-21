package net.corda.client.jfx.utils

import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class FlattenedListTest {

    lateinit var sourceList: ObservableList<SimpleObjectProperty<Int>>
    lateinit var flattenedList: ObservableList<Int>
    lateinit var replayedList: ObservableList<Int>

    @Before
    fun setup() {
        sourceList = FXCollections.observableArrayList(SimpleObjectProperty(1234))
        flattenedList = FlattenedList(sourceList)
        replayedList = ReplayedList(flattenedList)
    }

    @Test
    fun addWorks() {
        assertEquals(replayedList.size, 1)
        assertEquals(replayedList[0], 1234)

        sourceList.add(SimpleObjectProperty(12))
        assertEquals(replayedList.size, 2)
        assertEquals(replayedList[0], 1234)
        assertEquals(replayedList[1], 12)

        sourceList.add(SimpleObjectProperty(34))
        assertEquals(replayedList.size, 3)
        assertEquals(replayedList[0], 1234)
        assertEquals(replayedList[1], 12)
        assertEquals(replayedList[2], 34)

        sourceList.add(0, SimpleObjectProperty(56))
        assertEquals(replayedList.size, 4)
        assertEquals(replayedList[0], 56)
        assertEquals(replayedList[1], 1234)
        assertEquals(replayedList[2], 12)
        assertEquals(replayedList[3], 34)

        sourceList.addAll(2, listOf(SimpleObjectProperty(78), SimpleObjectProperty(910)))
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
        assertEquals(firstRemoved.get(), 1234)
        assertEquals(replayedList.size, 0)
        firstRemoved.set(123)

        sourceList.add(SimpleObjectProperty(12))
        sourceList.add(SimpleObjectProperty(34))
        sourceList.add(SimpleObjectProperty(56))
        assertEquals(replayedList.size, 3)
        val secondRemoved = sourceList.removeAt(1)
        assertEquals(secondRemoved.get(), 34)
        assertEquals(replayedList.size, 2)
        assertEquals(replayedList[0], 12)
        assertEquals(replayedList[1], 56)
        secondRemoved.set(123)

        sourceList.clear()
        assertEquals(replayedList.size, 0)
    }

    @Test
    fun updatingObservableWorks() {
        assertEquals(replayedList[0], 1234)
        sourceList[0].set(4321)
        assertEquals(replayedList[0], 4321)

        sourceList.add(0, SimpleObjectProperty(12))
        sourceList[1].set(8765)
        assertEquals(replayedList[0], 12)
        assertEquals(replayedList[1], 8765)

        sourceList[0].set(34)
        assertEquals(replayedList[0], 34)
        assertEquals(replayedList[1], 8765)
    }

    @Test
    fun reusingObservableWorks() {
        val observable = SimpleObjectProperty(12)
        sourceList.add(observable)
        sourceList.add(observable)
        assertEquals(replayedList.size, 3)
        assertEquals(replayedList[0], 1234)
        assertEquals(replayedList[1], 12)
        assertEquals(replayedList[2], 12)

        observable.set(34)
        assertEquals(replayedList.size, 3)
        assertEquals(replayedList[0], 1234)
        assertEquals(replayedList[1], 34)
        assertEquals(replayedList[2], 34)

        sourceList.removeAt(1)
        assertEquals(replayedList.size, 2)
        assertEquals(replayedList[0], 1234)
        assertEquals(replayedList[1], 34)
    }
}
