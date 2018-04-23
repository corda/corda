/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.client.jfx.utils

import javafx.collections.MapChangeListener
import javafx.collections.ObservableMap
import kotlin.test.assertEquals

/**
 * [ReplayedMap] simply replays changes done to the source map. Used for testing changes.
 */
class ReplayedMap<K, A>(sourceMap: ObservableMap<K, A>) : ReadOnlyBackedObservableMapBase<K, A, Unit>() {
    init {
        sourceMap.forEach {
            backingMap.set(it.key, Pair(it.value, Unit))
        }
        sourceMap.addListener { change: MapChangeListener.Change<out K, out A> ->
            if (change.wasRemoved()) {
                assertEquals(backingMap.remove(change.key)!!.first, change.valueRemoved)
            }
            if (change.wasAdded()) {
                backingMap.set(change.key, Pair(change.valueAdded, Unit))
            }
            fireChange(change)
        }
    }
}
