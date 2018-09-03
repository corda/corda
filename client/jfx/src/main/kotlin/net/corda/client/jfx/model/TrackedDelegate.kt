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
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

sealed class TrackedDelegate<M : Any>(val klass: KClass<M>) {
    init {
        Models.initModel(klass)
    }

    class ObservableDelegate<M : Any, T>(klass: KClass<M>, val observableProperty: (M) -> Observable<T>) : TrackedDelegate<M>(klass) {
        operator fun getValue(thisRef: Any, property: KProperty<*>): Observable<T> {
            return observableProperty(Models.get(klass, thisRef.javaClass.kotlin))
        }
    }

    class ObserverDelegate<M : Any, T>(klass: KClass<M>, val observerProperty: (M) -> Observer<T>) : TrackedDelegate<M>(klass) {
        operator fun getValue(thisRef: Any, property: KProperty<*>): Observer<T> {
            return observerProperty(Models.get(klass, thisRef.javaClass.kotlin))
        }
    }

    class SubjectDelegate<M : Any, T>(klass: KClass<M>, val subjectProperty: (M) -> Subject<T, T>) : TrackedDelegate<M>(klass) {
        operator fun getValue(thisRef: Any, property: KProperty<*>): Subject<T, T> {
            return subjectProperty(Models.get(klass, thisRef.javaClass.kotlin))
        }
    }

    class EventStreamDelegate<M : Any, T>(klass: KClass<M>, val eventStreamProperty: (M) -> EventStream<T>) : TrackedDelegate<M>(klass) {
        operator fun getValue(thisRef: Any, property: KProperty<*>): EventStream<T> {
            return eventStreamProperty(Models.get(klass, thisRef.javaClass.kotlin))
        }
    }

    class EventSinkDelegate<M : Any, T>(klass: KClass<M>, val eventSinkProperty: (M) -> EventSink<T>) : TrackedDelegate<M>(klass) {
        operator fun getValue(thisRef: Any, property: KProperty<*>): EventSink<T> {
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