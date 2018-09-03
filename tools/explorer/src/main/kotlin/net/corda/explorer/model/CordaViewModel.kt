package net.corda.explorer.model

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.ObservableList
import javafx.scene.Node
import tornadofx.*

class CordaViewModel {
    val selectedView = SimpleObjectProperty<CordaView>()
    val registeredViews = mutableListOf<CordaView>().observable()

    inline fun <reified T> registerView() where  T : CordaView {
        registerView(find<T>())
    }

    fun registerView(view: CordaView) {
        // Note: this is weirdly very important, as it forces the initialisation of Views. Therefore this is the entry
        // point to the top level observable/stream wiring! Any events sent before this init may be lost!
        registeredViews.add(view.apply { root })
    }
}

/**
 * Contain methods to construct various UI component used by the explorer UI framework.
 * TODO : Implement views with this interface and register in [CordaViewModel] when UI start up. We can use the [CordaViewModel] to dynamically create sidebar and dashboard without manual wiring.
 * TODO : "goto" functionality?
 */
abstract class CordaView(title: String? = null) : View(title) {
    open val widgets: ObservableList<CordaWidget> = emptyList<CordaWidget>().observable()
    abstract val icon: FontAwesomeIcon

    init {
        if (title == null) super.title = javaClass.simpleName
    }
}

data class CordaWidget(val name: String, val node: Node, val icon: FontAwesomeIcon? = null)