package com.r3corda.client.fxutils

import javafx.collections.MapChangeListener
import javafx.collections.ObservableMap

class ReplayedMap<K, A>(sourceMap: ObservableMap<K, A>) : ReadOnlyBackedObservableMapBase<K, A, Unit>() {
    init {
        sourceMap.forEach {
            backingMap.set(it.key, Pair(it.value, Unit))
        }
        sourceMap.addListener { change: MapChangeListener.Change<out K, out A> ->
            if (change.wasRemoved()) {
                require(backingMap.remove(change.key)!!.first == change.valueRemoved)
            }
            if (change.wasAdded()) {
                backingMap.set(change.key, Pair(change.valueAdded, Unit))
            }
            fireChange(change)
        }
    }
}
