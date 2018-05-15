package net.corda.demobench.ui

import com.sun.javafx.scene.control.behavior.TabPaneBehavior
import javafx.scene.Node
import javafx.scene.control.Tab
import tornadofx.*

/**
 * Using reflection, which works on both JDK8 and JDK9.
 * @see: <a href="https://bugs.openjdk.java.net/browse/JDK-8091261">JDK-8091261</a>
 */
class CloseableTab(text: String, content: Node) : Tab(text, content) {

    fun requestClose() {
        val skin = tabPane?.skin ?: return
        val field = skin.javaClass.findFieldByName("behavior") ?: return
        field.isAccessible = true

        val behaviour = field.get(skin) as? TabPaneBehavior ?: return
        if (behaviour.canCloseTab(this)) {
            behaviour.closeTab(this)
        }
    }
}
