package com.r3corda.client.fxutils

import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.collections.MapChangeListener
import javafx.collections.ObservableMap


fun <K, V> ObservableMap<K, V>.getObservableValue(key: K): ObservableValue<V?> {
    val property = SimpleObjectProperty(get(key))
    addListener { change: MapChangeListener.Change<out K, out V> ->
        if (change.key == key) {
            // This is true both when a fresh element was inserted and when an existing was updated
            if (change.wasAdded()) {
                property.set(change.valueAdded)
            } else if (change.wasRemoved()) {
                property.set(null)
            }
        }
    }
    return property
}
