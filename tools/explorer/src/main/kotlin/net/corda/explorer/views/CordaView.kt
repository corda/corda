package net.corda.explorer.views

import javafx.scene.Node

/**
 * Corda view interface, provides methods to construct various UI component used by the explorer UI framework.
 * TODO : Implement this interface on all views and register the views with ViewModel when UI start up, then we can use the ViewModel to dynamically create sidebar and dashboard without manual wiring.
 * TODO : Sidebar icons.
 */
interface CordaView {
    val widget: Node?
    val viewName: String
}
