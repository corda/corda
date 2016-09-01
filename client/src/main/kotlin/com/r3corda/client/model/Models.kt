package com.r3corda.client.model

import javafx.beans.property.ObjectProperty
import javafx.beans.value.ObservableValue
import javafx.beans.value.WritableValue
import javafx.collections.ObservableList
import org.reactfx.EventSink
import org.reactfx.EventStream
import rx.Observable
import rx.Observer
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * This file defines a global [Models] store and delegates to inject event streams/sinks. Note that all streams here
 * are global.
 *
 * This allows decoupling of UI logic from stream initialisation and provides us with a central place to inspect data
 * flows. It also allows detecting of looping logic by constructing a stream dependency graph TODO do this.
 *
 * General rule of thumb: A stream/observable should be a model if it may be reused several times.
 *
 * Usage:
 *  // Inject service -> client event stream
 *  private val serviceToClient: EventStream<ServiceToClientEvent> by eventStream(WalletMonitorModel::serviceToClient)
 *
 */

inline fun <reified M : Any, T> observable(noinline observableProperty: (M) -> Observable<T>) =
        TrackedDelegate.ObservableDelegate(M::class, observableProperty)

inline fun <reified M : Any, T> observer(noinline observerProperty: (M) -> Observer<T>) =
        TrackedDelegate.ObserverDelegate(M::class, observerProperty)

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

object Models {
    private val modelStore = HashMap<KClass<*>, Any>()
    private val dependencyGraph = HashMap<KClass<*>, MutableSet<KClass<*>>>()

    fun <M : Any> initModel(klass: KClass<M>) = modelStore.getOrPut(klass) { klass.java.newInstance() }
    fun <M : Any> get(klass: KClass<M>, origin: KClass<*>) : M {
        dependencyGraph.getOrPut(origin) { mutableSetOf<KClass<*>>() }.add(klass)
        val model = initModel(klass)
        if (model.javaClass != klass.java) {
            throw IllegalStateException("Model stored as ${klass.qualifiedName} has type ${model.javaClass}")
        }

        @Suppress("UNCHECKED_CAST")
        return model as M
    }
    inline fun <reified M : Any> get(origin: KClass<*>) : M = get(M::class, origin)
}

sealed class TrackedDelegate<M : Any>(val klass: KClass<M>) {
    init { Models.initModel(klass) }

    class ObservableDelegate<M : Any, T> (klass: KClass<M>, val eventStreamProperty: (M) -> Observable<T>) : TrackedDelegate<M>(klass) {
        operator fun getValue(thisRef: Any, property: KProperty<*>): Observable<T> {
            return eventStreamProperty(Models.get(klass, thisRef.javaClass.kotlin))
        }
    }
    class ObserverDelegate<M : Any, T> (klass: KClass<M>, val eventStreamProperty: (M) -> Observer<T>) : TrackedDelegate<M>(klass) {
        operator fun getValue(thisRef: Any, property: KProperty<*>): Observer<T> {
            return eventStreamProperty(Models.get(klass, thisRef.javaClass.kotlin))
        }
    }
    class EventStreamDelegate<M : Any, T> (klass: KClass<M>, val eventStreamProperty: (M) -> org.reactfx.EventStream<T>) : TrackedDelegate<M>(klass) {
        operator fun getValue(thisRef: Any, property: KProperty<*>): org.reactfx.EventStream<T> {
            return eventStreamProperty(Models.get(klass, thisRef.javaClass.kotlin))
        }
    }
    class EventSinkDelegate<M : Any, T> (klass: KClass<M>, val eventSinkProperty: (M) -> org.reactfx.EventSink<T>) : TrackedDelegate<M>(klass) {
        operator fun getValue(thisRef: Any, property: KProperty<*>): org.reactfx.EventSink<T> {
            return eventSinkProperty(Models.get(klass, thisRef.javaClass.kotlin))
        }
    }
    class ObservableValueDelegate<M : Any, T>(klass: KClass<M>, val observableValueProperty: (M) -> ObservableValue<T>) : TrackedDelegate<M>(klass) {
        operator fun getValue(thisRef: Any, property: KProperty<*>): ObservableValue<T> {
            return observableValueProperty(Models.get(klass, thisRef.javaClass.kotlin))
        }
    }
    class WritableValueDelegate<M : Any, T>(klass: KClass<M>, val writableValueProperty: (M) -> WritableValue<T>) : TrackedDelegate<M>(klass) {
        operator fun getValue(thisRef: Any, property: KProperty<*>): WritableValue<T> {
            return writableValueProperty(Models.get(klass, thisRef.javaClass.kotlin))
        }
    }
    class ObservableListDelegate<M : Any, T>(klass: KClass<M>, val observableListProperty: (M) -> ObservableList<T>) : TrackedDelegate<M>(klass) {
        operator fun getValue(thisRef: Any, property: KProperty<*>): ObservableList<T> {
            return observableListProperty(Models.get(klass, thisRef.javaClass.kotlin))
        }
    }
    class ObservableListReadOnlyDelegate<M : Any, out T>(klass: KClass<M>, val observableListReadOnlyProperty: (M) -> ObservableList<out T>) : TrackedDelegate<M>(klass) {
        operator fun getValue(thisRef: Any, property: KProperty<*>): ObservableList<out T> {
            return observableListReadOnlyProperty(Models.get(klass, thisRef.javaClass.kotlin))
        }
    }
    class ObjectPropertyDelegate<M : Any, T>(klass: KClass<M>, val objectPropertyProperty: (M) -> ObjectProperty<T>) : TrackedDelegate<M>(klass) {
        operator fun getValue(thisRef: Any, property: KProperty<*>): ObjectProperty<T> {
            return objectPropertyProperty(Models.get(klass, thisRef.javaClass.kotlin))
        }
    }
}
