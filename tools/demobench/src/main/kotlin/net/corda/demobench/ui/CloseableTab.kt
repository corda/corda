package net.corda.demobench.ui

import com.sun.javafx.scene.control.behavior.TabPaneBehavior
import com.sun.javafx.scene.control.skin.TabPaneSkin
import javafx.scene.Node
import javafx.scene.control.Tab

class CloseableTab(text: String, content: Node) : Tab(text, content) {

    fun requestClose() {
        val b = behaviour
        if (b.canCloseTab(this)) {
            b.closeTab(this)
        }
    }

    private val behaviour: TabPaneBehavior
        get() = (tabPane.skin as TabPaneSkin).behavior

}
