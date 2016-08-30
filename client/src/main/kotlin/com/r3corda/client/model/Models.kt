package com.r3corda.client.model

import javafx.beans.property.ObjectProperty
import javafx.beans.value.ObservableValue
import javafx.beans.value.WritableValue
import javafx.collections.ObservableList
import org.reactfx.EventSink
import org.reactfx.EventStream
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * This file defines a global [Models] store and delegates to inject event streams/sinks. Note that all streams here
 * are global to the app.
 *
 * This allows us to decouple UI logic from stream initialisation and provides us with a central place to inspect data flows.
 * It also allows detecting of looping logic by constructing a stream dependency graph TODO do this
 *
 * Usage: In your piece of UI use the [stream] and [sink] delegates to access external streams. If you have a reusable
 * stream put it in a Model. See [NetworkModel] for an example
 */

inline fun <reified M : Any, T> stream(noinline streamProperty: (M) -> EventStream<T>) =
        TrackedDelegate.Stream(M::class, streamProperty)

inline fun <reified M : Any, T> sink(noinline sinkProperty: (M) -> EventSink<T>) =
        TrackedDelegate.Sink(M::class, sinkProperty)

inline fun <reified M : Any, T> observableValue(noinline observableValueProperty: (M) -> ObservableValue<T>) =
        TrackedDelegate.ObservableValueDelegate(M::class, observableValueProperty)

inline fun <reified M : Any, T> writableValue(noinline writableValueProperty: (M) -> WritableValue<T>) =
        TrackedDelegate.WritableValueDelegate(M::class, writableValueProperty)

inline fun <reified M : Any, T> objectProperty(noinline objectProperty: (M) -> ObjectProperty<T>) =
        TrackedDelegate.ObjectPropertyDelegate(M::class, objectProperty)

inline fun <reified M : Any, T> observableList(noinline observableListProperty: (M) -> ObservableList<T>) =
        TrackedDelegate.ObservableListDelegate(M::class, observableListProperty)

inline fun <reified M : Any, T> observableListReadOnly(noinline observableListProperty: (M) -> ObservableList<out T>) =
        TrackedDelegate.ObservableListDelegateReadOnly(M::class, observableListProperty)

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

    class Stream<M : Any, T> (klass: KClass<M>, val streamProperty: (M) -> EventStream<T>) : TrackedDelegate<M>(klass) {
        operator fun getValue(thisRef: Any, property: KProperty<*>): EventStream<T> {
            return streamProperty(Models.get(klass, thisRef.javaClass.kotlin))
        }
    }

    class Sink<M : Any, T> (klass: KClass<M>, val sinkProperty: (M) -> EventSink<T>) : TrackedDelegate<M>(klass) {
        operator fun getValue(thisRef: Any, property: KProperty<*>): EventSink<T> {
            return sinkProperty(Models.get(klass, thisRef.javaClass.kotlin))
        }
    }
    class ObservableValueDelegate<M : Any, T>(klass: KClass<M>, val sinkProperty: (M) -> ObservableValue<T>) : TrackedDelegate<M>(klass) {
        operator fun getValue(thisRef: Any, property: KProperty<*>): ObservableValue<T> {
            return sinkProperty(Models.get(klass, thisRef.javaClass.kotlin))
        }
    }
    class WritableValueDelegate<M : Any, T>(klass: KClass<M>, val sinkProperty: (M) -> WritableValue<T>) : TrackedDelegate<M>(klass) {
        operator fun getValue(thisRef: Any, property: KProperty<*>): WritableValue<T> {
            return sinkProperty(Models.get(klass, thisRef.javaClass.kotlin))
        }
    }
    class ObservableListDelegate<M : Any, T>(klass: KClass<M>, val sinkProperty: (M) -> ObservableList<T>) : TrackedDelegate<M>(klass) {
        operator fun getValue(thisRef: Any, property: KProperty<*>): ObservableList<T> {
            return sinkProperty(Models.get(klass, thisRef.javaClass.kotlin))
        }
    }
    class ObservableListDelegateReadOnly<M : Any, T>(klass: KClass<M>, val sinkProperty: (M) -> ObservableList<out T>) : TrackedDelegate<M>(klass) {
        operator fun getValue(thisRef: Any, property: KProperty<*>): ObservableList<out T> {
            return sinkProperty(Models.get(klass, thisRef.javaClass.kotlin))
        }
    }
    class ObjectPropertyDelegate<M : Any, T>(klass: KClass<M>, val sinkProperty: (M) -> ObjectProperty<T>) : TrackedDelegate<M>(klass) {
        operator fun getValue(thisRef: Any, property: KProperty<*>): ObjectProperty<T> {
            return sinkProperty(Models.get(klass, thisRef.javaClass.kotlin))
        }
    }
}
