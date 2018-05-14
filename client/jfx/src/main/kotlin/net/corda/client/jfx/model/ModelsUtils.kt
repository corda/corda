/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

@file:JvmName("ModelsUtils")

package net.corda.client.jfx.model

import javafx.beans.property.ObjectProperty
import javafx.beans.value.ObservableValue
import javafx.beans.value.WritableValue
import javafx.collections.ObservableList
import org.reactfx.EventSink
import org.reactfx.EventStream
import rx.Observable
import rx.Observer
import rx.subjects.Subject

inline fun <reified M : Any, T> observable(noinline observableProperty: (M) -> Observable<T>) =
        TrackedDelegate.ObservableDelegate(M::class, observableProperty)

inline fun <reified M : Any, T> observer(noinline observerProperty: (M) -> Observer<T>) =
        TrackedDelegate.ObserverDelegate(M::class, observerProperty)

inline fun <reified M : Any, T> subject(noinline subjectProperty: (M) -> Subject<T, T>) =
        TrackedDelegate.SubjectDelegate(M::class, subjectProperty)

inline fun <reified M : Any, T> eventStream(noinline streamProperty: (M) -> EventStream<T>) =
        TrackedDelegate.EventStreamDelegate(M::class, streamProperty)

inline fun <reified M : Any, T> eventSink(noinline sinkProperty: (M) -> EventSink<T>) =
        TrackedDelegate.EventSinkDelegate(M::class, sinkProperty)

inline fun <reified M : Any, T> observableValue(noinline observableValueProperty: (M) -> ObservableValue<T>) =
        TrackedDelegate.ObservableValueDelegate(M::class, observableValueProperty)

inline fun <reified M : Any, T> writableValue(noinline writableValueProperty: (M) -> WritableValue<T>) =
        TrackedDelegate.WritableValueDelegate(M::class, writableValueProperty)

inline fun <reified M : Any, T> objectProperty(noinline objectProperty: (M) -> ObjectProperty<T>) =
        TrackedDelegate.ObjectPropertyDelegate(M::class, objectProperty)

inline fun <reified M : Any, T> observableList(noinline observableListProperty: (M) -> ObservableList<T>) =
        TrackedDelegate.ObservableListDelegate(M::class, observableListProperty)

inline fun <reified M : Any, T> observableListReadOnly(noinline observableListProperty: (M) -> ObservableList<out T>) =
        TrackedDelegate.ObservableListReadOnlyDelegate(M::class, observableListProperty)