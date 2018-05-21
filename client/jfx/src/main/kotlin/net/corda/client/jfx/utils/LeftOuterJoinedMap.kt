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
            val rightValueProperty = SimpleObjectProperty(rightTable[entry.key])
            backingMap[entry.key] = Pair(assemble(entry.key, entry.value, rightValueProperty), rightValueProperty)
        }

        leftTable.addListener { change: MapChangeListener.Change<out K, out A> ->
            var addedValue: C? = null
            var removedValue: C? = null

            if (change.wasRemoved()) {
                removedValue = backingMap.remove(change.key)?.first
            }

            if (change.wasAdded()) {
                val rightValue = rightTable[change.key]
                val rightValueProperty = SimpleObjectProperty(rightValue)
                val newValue = assemble(change.key, change.valueAdded, rightValueProperty)
                backingMap[change.key] = Pair(newValue, rightValueProperty)
                addedValue = newValue
            }

            fireChange(createMapChange(change.key, removedValue, addedValue))
        }
        rightTable.addListener { change: MapChangeListener.Change<out K, out B> ->
            if (change.wasRemoved() && !change.wasAdded()) {
                backingMap[change.key]?.second?.set(null)
            }

            if (change.wasAdded()) {
                backingMap[change.key]?.second?.set(change.valueAdded)
            }
        }
    }
}
