package net.corda.client.jfx.utils

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.collections.ObservableMap
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class AssociatedListTest {

    lateinit var sourceList: ObservableList<Int>
    lateinit var associatedList: ObservableMap<Int, Int>
    lateinit var replayedMap: ObservableMap<Int, Int>

    @Before
    fun setup() {
        sourceList = FXCollections.observableArrayList(0)
        associatedList = AssociatedList(sourceList, { it % 3 }) { _, number -> number }
        replayedMap = ReplayedMap(associatedList)
    }

    @Test
    fun addWorks() {
        assertEquals(replayedMap.size, 1)
        assertEquals(replayedMap[0], 0)

        sourceList.add(2)
        assertEquals(replayedMap.size, 2)
        assertEquals(replayedMap[0], 0)
        assertEquals(replayedMap[2], 2)

        sourceList.add(0, 4)
        assertEquals(replayedMap.size, 3)
        assertEquals(replayedMap[0], 0)
        assertEquals(replayedMap[2], 2)
        assertEquals(replayedMap[1], 4)
    }

    @Test
    fun removeWorks() {
        sourceList.addAll(2, 4)
        assertEquals(replayedMap.size, 3)

        sourceList.removeAt(0)
        assertEquals(replayedMap.size, 2)
        assertEquals(replayedMap[2], 2)
        assertEquals(replayedMap[1], 4)

        sourceList.add(1, 12)
        assertEquals(replayedMap.size, 3)
        assertEquals(replayedMap[2], 2)
        assertEquals(replayedMap[1], 4)
        assertEquals(replayedMap[0], 12)

        sourceList.clear()
        assertEquals(replayedMap.size, 0)
    }

    @Test
    fun updateWorks() {
        sourceList.addAll(2, 4)
        assertEquals(replayedMap.size, 3)

        sourceList[1] = 5
        assertEquals(replayedMap.size, 3)
        assertEquals(replayedMap[0], 0)
        assertEquals(replayedMap[2], 5)
        assertEquals(replayedMap[1], 4)

        sourceList.removeAt(1)
        assertEquals(replayedMap.size, 2)
        assertEquals(replayedMap[0], 0)
        assertEquals(replayedMap[1], 4)
    }
}
