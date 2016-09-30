package com.r3corda.client.fxutils

import javafx.application.Platform
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.collections.ObservableMap
import rx.Observable

/**
 * Simple utilities for converting an [rx.Observable] into a javafx [ObservableValue]/[ObservableList]
 */

/**
 * [foldToObservableValue] takes an [rx.Observable] stream and creates an [ObservableValue] out of it.
 * @param initial The initial value of the returned observable.
 * @param folderFun The transformation function to be called on the observable value when a new element is emitted on
 *     the stream.
 */
fun <A, B> Observable<A>.foldToObservableValue(initial: B, folderFun: (A, B) -> B): ObservableValue<B> {
    val result = SimpleObjectProperty<B>(initial)
    subscribe {
        Platform.runLater {
            result.set(folderFun(it, result.get()))
        }
    }
    return result
}

/**
 * [foldToObservableList] takes an [rx.Observable] stream and creates an [ObservableList] out of it, while maintaining
 * an accumulator.
 * @param initialAccumulator The initial value of the accumulator.
 * @param folderFun The transformation function to be called on the observable list when a new element is emitted on
 *     the stream, which should modify the list as needed.
 */
fun <A, B, C> Observable<A>.foldToObservableList(
        initialAccumulator: C, folderFun: (A, C, ObservableList<B>) -> C
): ObservableList<B> {
    val result = FXCollections.observableArrayList<B>()
    /**
     * This capture is fine, as [Platform.runLater] runs closures in order
     */
    var currentAccumulator = initialAccumulator
    subscribe {
        Platform.runLater {
            currentAccumulator = folderFun(it, currentAccumulator, result)
        }
    }
    return result
}

/**
 * [recordInSequence] records incoming events on the [rx.Observable] in sequence.
 */
fun <A> Observable<A>.recordInSequence(): ObservableList<A> {
    return foldToObservableList(Unit) { newElement, _unit, list ->
        list.add(newElement)
    }
}

/**
 * [foldToObservableMap] takes an [rx.Observable] stream and creates an [ObservableMap] out of it, while maintaining
 * an accumulator.
 * @param initialAccumulator The initial value of the accumulator.
 * @param folderFun The transformation function to be called on the observable map when a new element is emitted on
 *     the stream, which should modify the map as needed.
 */
fun <A, B, K, C> Observable<A>.foldToObservableMap(
        initialAccumulator: C, folderFun: (A, C, ObservableMap<K, B>) -> C
): ObservableMap<K, out B> {
    val result = FXCollections.observableHashMap<K, B>()
    /**
     * This capture is fine, as [Platform.runLater] runs closures in order
     */
    var currentAccumulator = initialAccumulator
    subscribe {
        Platform.runLater {
            currentAccumulator = folderFun(it, currentAccumulator, result)
        }
    }
    return result
}

/**
 * This variant simply associates each event with its key.
 * @param toKey Function retrieving the key to associate with.
 * @param merge The function to be called if there is an existing element at the key.
 */
fun <A, K> Observable<A>.recordAsAssociation(
        toKey: (A) -> K,
        merge: (K, oldValue: A, newValue: A) -> A = { _key, _oldValue, newValue -> newValue }
): ObservableMap<K, out A> {
    return foldToObservableMap(Unit) { newElement, _unit, map ->
        val key = toKey(newElement)
        val oldValue = map.get(key)
        if (oldValue != null) {
            map.set(key, merge(key, oldValue, newElement))
        } else {
            map.set(key, newElement)
        }
    }
}
