package com.r3corda.client.fxutils

import javafx.collections.FXCollections
import javafx.collections.MapChangeListener
import javafx.collections.ObservableList
import javafx.collections.ObservableMap
import kotlin.comparisons.compareValues

/**
 * [MapValuesList] takes an [ObservableMap] and returns its values as an [ObservableList].
 * The order of returned elements is deterministic but unspecified.
 */
class MapValuesList<K, A> private constructor(
        sourceMap: ObservableMap<K, A>,
        private val backingList: ObservableList<Map.Entry<K, A>>, // sorted by K.hashCode()
        private val exposedList: ObservableList<A>
) : ObservableList<A> by exposedList {

    companion object {
        fun <K, A> create(sourceMap: ObservableMap<K, A>): MapValuesList<K, A> {
            val backingList = FXCollections.observableArrayList<Map.Entry<K, A>>(sourceMap.entries.sortedBy { it.key!!.hashCode() })
            return MapValuesList(sourceMap, backingList, backingList.map { it.value })
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
