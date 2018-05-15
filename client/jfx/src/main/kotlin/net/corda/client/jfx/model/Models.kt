/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.client.jfx.model

import net.corda.core.internal.uncheckedCast
import java.util.*
import kotlin.reflect.KClass

/**
 * Global models store to allow decoupling of UI logic from stream initialisation and provide a central place to
 * inspect data flows. It also allows detecting of looping logic by constructing a stream dependency graph TODO do this.
 *
 * Usage:
 *  // Inject service -> client event stream
 *  private val serviceToClient: EventStream<ServiceToClientEvent> by Models.eventStream(WalletMonitorModel::serviceToClient)
 *
 * Each `Screen` code should have a code layout like this:
 *
 * class Screen {
 *   val root = (..)
 *
 *   [ inject UI elements using fxid()/inject() ]
 *
 *   [ inject observable dependencies using observable()/eventSink() etc]
 *
 *   [ define screen-specific observables ]
 *
 *   init {
 *     [ wire up UI elements ]
 *   }
 * }
 *
 * For example if I wanted to display a list of all USD cash states:
 *
 * class USDCashStatesScreen {
 *   val root: Pane by fxml()
 *
 *   val usdCashStatesListView: ListView<Cash.State> by fxid("USDCashStatesListView")
 *
 *   val cashStates: ObservableList<Cash.State> by Models.observableList(ContractStateModel::cashStates)
 *
 *   val usdCashStates = cashStates.filter { it.(..).currency == USD }
 *
 *   init {
 *     Bindings.bindContent(usdCashStatesListView.items, usdCashStates)
 *     usdCashStatesListView.setCellValueFactory(somethingsomething)
 *   }
 * }
 *
 * The UI code can just assume that the cash state list comes from somewhere outside. The initialisation of that
 * observable is decoupled, it may be mocked or be streamed from the network etc.
 *
 * Later on we may even want to move all screen-specific observables to a separate Model as well (like usdCashStates) - this
 * would allow moving all of the aggregation logic to e.g. a different machine, all the UI will do is inject these and wire
 * them up with the UI elements.
 *
 * Another advantage of this separation is that once we start adding a lot of screens we can still track data dependencies
 * in a central place as opposed to ad-hoc wiring up the observables.
 */
object Models {
    private val modelStore = HashMap<KClass<*>, Any>()

    /**
     * Holds a class->dependencies map that tracks what screens are depending on what model.
     */
    private val dependencyGraph = HashMap<KClass<*>, MutableSet<KClass<*>>>()

    fun <M : Any> initModel(klass: KClass<M>) = modelStore.getOrPut(klass) { klass.java.newInstance() }
    fun <M : Any> get(klass: KClass<M>, origin: KClass<*>): M {
        dependencyGraph.getOrPut(origin) { mutableSetOf() }.add(klass)
        val model = initModel(klass)
        if (model.javaClass != klass.java) {
            throw IllegalStateException("Model stored as ${klass.qualifiedName} has type ${model.javaClass}")
        }
        return uncheckedCast(model)
    }

    inline fun <reified M : Any> get(origin: KClass<*>): M = get(M::class, origin)
}

