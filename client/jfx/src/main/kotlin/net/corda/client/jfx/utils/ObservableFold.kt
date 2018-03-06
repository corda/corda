/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

@file:JvmName("ObservableFold")

package net.corda.client.jfx.utils

import javafx.application.Platform
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.collections.ObservableMap
import rx.Observable
import java.util.concurrent.TimeUnit

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
 * [fold] takes an [rx.Observable] stream and applies fold function on it, and collects all elements using the accumulator.
 * @param accumulator The accumulator for accumulating elements.
 * @param folderFun The transformation function to be called on the observable list when a new element is emitted on
 *     the stream, which should modify the list as needed.
 */
fun <T, R> Observable<T>.fold(accumulator: R, folderFun: (R, T) -> Unit): R {
    /**
     * This capture is fine, as [Platform.runLater] runs closures in order.
     * The buffer is to avoid flooding FX thread with runnable.
     */
    buffer(1, TimeUnit.SECONDS).subscribe {
        if (it.isNotEmpty()) {
            Platform.runLater {
                it.fold(accumulator) { list, item ->
                    folderFun.invoke(list, item)
                    list
                }
            }
        }
    }
    return accumulator
}

/**
 * [recordInSequence] records incoming events on the [rx.Observable] in sequence.
 */
fun <A> Observable<A>.recordInSequence(): ObservableList<A> {
    return fold(FXCollections.observableArrayList()) { list, newElement ->
        list.add(newElement)
    }
}

/**
 * This variant simply associates each event with its key.
 * @param toKey Function retrieving the key to associate with.
 * @param merge The function to be called if there is an existing element at the key.
 */
fun <A, K> Observable<A>.recordAsAssociation(toKey: (A) -> K, merge: (K, oldValue: A, newValue: A) -> A = { _, _, newValue -> newValue }): ObservableMap<K, A> {
    return fold(FXCollections.observableHashMap<K, A>()) { map, item ->
        val key = toKey(item)
        map[key] = map[key]?.let { merge(key, it, item) } ?: item
    }
}
