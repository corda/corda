package com.r3corda.client.fxutils

import javafx.collections.FXCollections
import org.junit.Before
import org.junit.Test
import kotlin.test.fail

class AggregatedListTest {

    var sourceList = FXCollections.observableArrayList<Int>()
    var aggregatedList = AggregatedList(sourceList, { it % 3 }) { mod3, group -> Pair(mod3, group) }
    var replayedList = ReplayedList(aggregatedList)

    @Before
    fun setup() {
        sourceList = FXCollections.observableArrayList<Int>()
        aggregatedList = AggregatedList(sourceList, { it % 3 }) { mod3, group -> Pair(mod3, group) }
        replayedList = ReplayedList(aggregatedList)
    }

    @Test
    fun addWorks() {
        require(replayedList.size == 0) { "Aggregation is empty is source list is" }

        sourceList.add(9)
        require(replayedList.size == 1) { "Aggregation list has one element if one was added to source list" }
        require(replayedList[0]!!.first == 0)

        sourceList.add(8)
        require(replayedList.size == 2) { "Aggregation list has two elements if two were added to source list with different keys" }

        sourceList.add(6)
        require(replayedList.size == 2) { "Aggregation list's size doesn't change if element with existing key is added" }

        replayedList.forEach {
            when (it.first) {
                0 -> require(it.second.toSet() == setOf(6, 9))
                2 -> require(it.second.size == 1)
                else -> fail("No aggregation expected with key ${it.first}")
            }
        }
    }

    @Test
    fun removeWorks() {
        sourceList.addAll(0, 1, 2, 3, 4)

        require(replayedList.size == 3)
        replayedList.forEach {
            when (it.first) {
                0 -> require(it.second.toSet() == setOf(0, 3))
                1 -> require(it.second.toSet() == setOf(1, 4))
                2 -> require(it.second.toSet() == setOf(2))
                else -> fail("No aggregation expected with key ${it.first}")
            }
        }

        sourceList.remove(4)
        require(replayedList.size == 3)
        replayedList.forEach {
            when (it.first) {
                0 -> require(it.second.toSet() == setOf(0, 3))
                1 -> require(it.second.toSet() == setOf(1))
                2 -> require(it.second.toSet() == setOf(2))
                else -> fail("No aggregation expected with key ${it.first}")
            }
        }

        sourceList.remove(2, 4)
        require(replayedList.size == 2)
        replayedList.forEach {
            when (it.first) {
                0 -> require(it.second.toSet() == setOf(0))
                1 -> require(it.second.toSet() == setOf(1))
                else -> fail("No aggregation expected with key ${it.first}")
            }
        }

        sourceList.removeAll(0, 1)
        require(replayedList.size == 0)
    }
}


