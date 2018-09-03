package net.corda.client.jfx.utils

import com.sun.javafx.collections.MapListenerHelper
import javafx.beans.InvalidationListener
import javafx.collections.MapChangeListener
import javafx.collections.ObservableMap
import java.util.*

/**
 * [ReadOnlyBackedObservableMapBase] is a base class implementing all abstract functions required for an [ObservableMap]
 * using a backing HashMap that subclasses should modify.
 *
 * Non-read-only API calls throw.
 *
 * @param K The key type.
 * @param A The exposed map element type.
 * @param B Auxiliary data subclasses may wish to store in the backing map.
 */
open class ReadOnlyBackedObservableMapBase<K, A, B> : ObservableMap<K, A> {
    protected val backingMap = HashMap<K, Pair<A, B>>()
    private var mapListenerHelper: MapListenerHelper<K, A>? = null

    protected fun fireChange(change: MapChangeListener.Change<out K, out A>) {
        MapListenerHelper.fireValueChangedEvent(mapListenerHelper, change)
    }

    override fun addListener(listener: InvalidationListener) {
        mapListenerHelper = MapListenerHelper.addListener(mapListenerHelper, listener)
    }

    override fun addListener(listener: MapChangeListener<in K, in A>?) {
        mapListenerHelper = MapListenerHelper.addListener(mapListenerHelper, listener)
    }

    override fun removeListener(listener: InvalidationListener?) {
        mapListenerHelper = MapListenerHelper.removeListener(mapListenerHelper, listener)
    }

    override fun removeListener(listener: MapChangeListener<in K, in A>?) {
        mapListenerHelper = MapListenerHelper.removeListener(mapListenerHelper, listener)
    }

    override val size: Int get() = backingMap.size

    override fun containsKey(key: K) = backingMap.containsKey(key)

    override fun containsValue(value: A) = backingMap.any { it.value.first == value }

    override fun get(key: K) = backingMap[key]?.first

    override fun isEmpty() = backingMap.isEmpty()

    override val entries: MutableSet<MutableMap.MutableEntry<K, A>>
        get() = backingMap.entries.fold(mutableSetOf()) { set, entry ->
            set.add(object : MutableMap.MutableEntry<K, A> {
                override var value: A = entry.value.first
                override val key = entry.key
                override fun setValue(newValue: A): A {
                    val old = value
                    value = newValue
                    return old
                }
            })
            set
        }
    override val keys: MutableSet<K> get() = backingMap.keys
    override val values: MutableCollection<A> get() = ArrayList(backingMap.values.map { it.first })

    override fun clear() {
        throw UnsupportedOperationException("clear() can't be called on ReadOnlyObservableMapBase")
    }

    override fun put(key: K, value: A): A {
        throw UnsupportedOperationException("put() can't be called on ReadOnlyObservableMapBase")
    }

    override fun putAll(from: Map<out K, A>) {
        throw UnsupportedOperationException("putAll() can't be called on ReadOnlyObservableMapBase")
    }

    override fun remove(key: K): A {
        throw UnsupportedOperationException("remove() can't be called on ReadOnlyObservableMapBase")
    }

    /**
     * Construct an object modelling the given change to an observed map.
     */
    fun createMapChange(key: K, removedValue: A?, addedValue: A?): MapChangeListener.Change<K, A> {
        return object : MapChangeListener.Change<K, A>(this) {
            override fun getKey() = key
            override fun wasRemoved() = removedValue != null
            override fun wasAdded() = addedValue != null
            override fun getValueRemoved() = removedValue
            override fun getValueAdded() = addedValue
        }
    }
}
