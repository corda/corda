package com.r3corda.client.fxutils

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import org.junit.Before
import org.junit.Test
import java.util.*

class ConcatenatedListTest {

    var sourceList = FXCollections.observableArrayList<ObservableList<String>>(FXCollections.observableArrayList("hello"))
    var concatenatedList = ConcatenatedList(sourceList)
    var replayedList = ReplayedList(concatenatedList)

    @Before
    fun setup() {
        sourceList = FXCollections.observableArrayList<ObservableList<String>>(FXCollections.observableArrayList("hello"))
        concatenatedList = ConcatenatedList(sourceList)
        replayedList = ReplayedList(concatenatedList)
    }

    @Test
    fun addWorks() {
        require(replayedList.size == 1)
        require(replayedList[0] == "hello")

        sourceList.add(FXCollections.observableArrayList("a", "b"))
        require(replayedList.size == 3)
        require(replayedList[0] == "hello")
        require(replayedList[1] == "a")
        require(replayedList[2] == "b")

        sourceList.add(1, FXCollections.observableArrayList("c"))
        require(replayedList.size == 4)
        require(replayedList[0] == "hello")
        require(replayedList[1] == "c")
        require(replayedList[2] == "a")
        require(replayedList[3] == "b")

        sourceList[0].addAll("d", "e")
        require(replayedList.size == 6)
        require(replayedList[0] == "hello")
        require(replayedList[1] == "d")
        require(replayedList[2] == "e")
        require(replayedList[3] == "c")
        require(replayedList[4] == "a")
        require(replayedList[5] == "b")

        sourceList[1].add(0, "f")
        require(replayedList.size == 7)
        require(replayedList[0] == "hello")
        require(replayedList[1] == "d")
        require(replayedList[2] == "e")
        require(replayedList[3] == "f")
        require(replayedList[4] == "c")
        require(replayedList[5] == "a")
        require(replayedList[6] == "b")
    }

    @Test
    fun removeWorks() {
        sourceList.add(FXCollections.observableArrayList("a", "b"))
        sourceList.add(1, FXCollections.observableArrayList("c"))
        sourceList[0].addAll("d", "e")
        sourceList[1].add(0, "f")

        sourceList.removeAt(1)
        require(replayedList.size == 5)
        require(replayedList[0] == "hello")
        require(replayedList[1] == "d")
        require(replayedList[2] == "e")
        require(replayedList[3] == "a")
        require(replayedList[4] == "b")

        sourceList[0].clear()
        require(replayedList.size == 2)
        require(replayedList[0] == "a")
        require(replayedList[1] == "b")
    }

    @Test
    fun permutationWorks() {
        sourceList.addAll(FXCollections.observableArrayList("a", "b"), FXCollections.observableArrayList("c"))
        require(replayedList.size == 4)
        require(replayedList[0] == "hello")
        require(replayedList[1] == "a")
        require(replayedList[2] == "b")
        require(replayedList[3] == "c")

        sourceList.sortWith(object : Comparator<ObservableList<String>> {
            override fun compare(p0: ObservableList<String>, p1: ObservableList<String>): Int {
                return p0.size - p1.size
            }
        })
        require(replayedList.size == 4)
        require(replayedList[0] == "hello")
        require(replayedList[1] == "c")
        require(replayedList[2] == "a")
        require(replayedList[3] == "b")

        sourceList.add(0, FXCollections.observableArrayList("d", "e", "f"))
        sourceList.sortWith(object : Comparator<ObservableList<String>> {
            override fun compare(p0: ObservableList<String>, p1: ObservableList<String>): Int {
                return p0.size - p1.size
            }
        })
        require(replayedList.size == 7)
        require(replayedList[0] == "hello")
        require(replayedList[1] == "c")
        require(replayedList[2] == "a")
        require(replayedList[3] == "b")
        require(replayedList[4] == "d")
        require(replayedList[5] == "e")
        require(replayedList[6] == "f")
    }

}
