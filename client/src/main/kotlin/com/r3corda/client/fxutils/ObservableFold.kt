package com.r3corda.client.fxutils

import javafx.application.Platform
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import rx.Observable

/**
 * Simple utilities for converting an [rx.Observable] into a javafx [ObservableValue]/[ObservableList]
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
 * This variant simply exposes all events in the list, in order of arrival.
 */
fun <A> Observable<A>.foldToObservableList(): ObservableList<A> {
    return foldToObservableList(Unit) { newElement, _unit, list ->
        list.add(newElement)
    }
}
