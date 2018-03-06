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
import javafx.collections.MapChangeListener
import javafx.collections.ObservableList
import javafx.collections.ObservableMap

/**
 * [MapValuesList] takes an [ObservableMap] and returns its values as an [ObservableList].
 * The order of returned elements is deterministic but unspecified.
 */
class MapValuesList<K, A, C> private constructor(
        val sourceMap: ObservableMap<K, A>,
        private val backingList: ObservableList<Map.Entry<K, A>>, // sorted by K.hashCode()
        private val exposedList: ObservableList<C>
) : ObservableList<C> by exposedList {

    companion object {
        /**
         * [create] is the factory of [MapValuesList].
         * @param sourceMap The source map.
         * @param assemble The function to be called for map each entry to construct the final list elements.
         */
        fun <K, A, C> create(sourceMap: ObservableMap<K, A>, assemble: (Map.Entry<K, A>) -> C): MapValuesList<K, A, C> {
            val backingList = FXCollections.observableArrayList<Map.Entry<K, A>>(sourceMap.entries.sortedBy { it.key!!.hashCode() })
            return MapValuesList(sourceMap, backingList, backingList.map { assemble(it) })
        }
    }

    init {
        sourceMap.addListener { change: MapChangeListener.Change<out K, out A> ->
            val keyHashCode = change.key!!.hashCode()
            if (change.wasRemoved()) {
                val removeIndex = backingList.binarySearch(
                        comparison = { entry -> compareValues(keyHashCode, entry.key!!.hashCode()) }
                )
                if (removeIndex < 0) {
                    throw IllegalStateException("Removed value does not map")
                }
                if (change.wasAdded()) {
                    backingList[removeIndex] = object : Map.Entry<K, A> {
                        override val key = change.key
                        override val value = change.valueAdded
                    }
                } else {
                    backingList.removeAt(removeIndex)
                }
            } else if (change.wasAdded()) {
                val index = backingList.binarySearch(
                        comparison = { entry -> compareValues(keyHashCode, entry.key!!.hashCode()) }
                )
                val addIndex = -index - 1
                backingList.add(addIndex, object : Map.Entry<K, A> {
                    override val key = change.key
                    override val value = change.valueAdded
                })
            }
        }
    }
}
