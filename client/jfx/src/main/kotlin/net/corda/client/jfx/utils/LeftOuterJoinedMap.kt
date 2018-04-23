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

import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.collections.MapChangeListener
import javafx.collections.ObservableMap

/**
 * [LeftOuterJoinedMap] implements a special case of a left outer join where we're matching on primary keys of both
 * tables.
 */
class LeftOuterJoinedMap<K : Any, A, B, C>(
        val leftTable: ObservableMap<K, out A>,
        val rightTable: ObservableMap<K, out B>,
        assemble: (K, A, ObservableValue<B?>) -> C
) : ReadOnlyBackedObservableMapBase<K, C, SimpleObjectProperty<B?>>() {
    init {
        leftTable.forEach { entry ->
            val rightValueProperty = SimpleObjectProperty(rightTable.get(entry.key))
            backingMap.set(entry.key, Pair(assemble(entry.key, entry.value, rightValueProperty), rightValueProperty))
        }

        leftTable.addListener { change: MapChangeListener.Change<out K, out A> ->
            var addedValue: C? = null
            var removedValue: C? = null

            if (change.wasRemoved()) {
                removedValue = backingMap.remove(change.key)?.first
            }

            if (change.wasAdded()) {
                val rightValue = rightTable.get(change.key)
                val rightValueProperty = SimpleObjectProperty(rightValue)
                val newValue = assemble(change.key, change.valueAdded, rightValueProperty)
                backingMap.set(change.key, Pair(newValue, rightValueProperty))
                addedValue = newValue
            }

            fireChange(createMapChange(change.key, removedValue, addedValue))
        }
        rightTable.addListener { change: MapChangeListener.Change<out K, out B> ->
            if (change.wasRemoved() && !change.wasAdded()) {
                backingMap.get(change.key)?.second?.set(null)
            }

            if (change.wasAdded()) {
                backingMap.get(change.key)?.second?.set(change.valueAdded)
            }
        }
    }
}
