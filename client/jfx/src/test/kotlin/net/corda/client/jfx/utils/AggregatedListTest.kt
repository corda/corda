/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.client.jfx.utils

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class AggregatedListTest {

    lateinit var sourceList: ObservableList<Int>
    lateinit var aggregatedList: ObservableList<Pair<Int, ObservableList<Int>>>
    lateinit var replayedList: ObservableList<Pair<Int, ObservableList<Int>>>

    @Before
    fun setup() {
        sourceList = FXCollections.observableArrayList<Int>()
        aggregatedList = AggregatedList(sourceList, { it % 3 }) { mod3, group -> Pair(mod3, group) }
        replayedList = ReplayedList(aggregatedList)
    }

    @Test
    fun addWorks() {
        assertEquals(replayedList.size, 0)

        sourceList.add(9)
        assertEquals(replayedList.size, 1)
        assertEquals(replayedList[0]!!.first, 0)

        sourceList.add(8)
        assertEquals(replayedList.size, 2)

        sourceList.add(6)
        assertEquals(replayedList.size, 2)

        replayedList.forEach {
            when (it.first) {
                0 -> assertEquals(it.second.toSet(), setOf(6, 9))
                2 -> assertEquals(it.second.size, 1)
                else -> fail("No aggregation expected with key ${it.first}")
            }
        }
    }

    @Test
    fun removeWorks() {
        sourceList.addAll(0, 1, 2, 3, 4)

        assertEquals(replayedList.size, 3)
        replayedList.forEach {
            when (it.first) {
                0 -> assertEquals(it.second.toSet(), setOf(0, 3))
                1 -> assertEquals(it.second.toSet(), setOf(1, 4))
                2 -> assertEquals(it.second.toSet(), setOf(2))
                else -> fail("No aggregation expected with key ${it.first}")
            }
        }

        sourceList.remove(4)
        assertEquals(replayedList.size, 3)
        replayedList.forEach {
            when (it.first) {
                0 -> assertEquals(it.second.toSet(), setOf(0, 3))
                1 -> assertEquals(it.second.toSet(), setOf(1))
                2 -> assertEquals(it.second.toSet(), setOf(2))
                else -> fail("No aggregation expected with key ${it.first}")
            }
        }

        sourceList.remove(2, 4)
        assertEquals(replayedList.size, 2)
        replayedList.forEach {
            when (it.first) {
                0 -> assertEquals(it.second.toSet(), setOf(0))
                1 -> assertEquals(it.second.toSet(), setOf(1))
                else -> fail("No aggregation expected with key ${it.first}")
            }
        }

        sourceList.removeAll(0, 1)
        assertEquals(replayedList.size, 0)
    }

    @Test
    fun multipleElementsWithSameHashWorks() {
        sourceList.addAll(0, 0)
        assertEquals(replayedList.size, 1)
        replayedList.forEach {
            when (it.first) {
                0 -> {
                    assertEquals(it.second.size, 2)
                    assertEquals(it.second[0], 0)
                    assertEquals(it.second[1], 0)
                }
                else -> fail("No aggregation expected with key ${it.first}")
            }
        }
    }
}


