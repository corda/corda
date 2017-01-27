package net.corda.demobench.views

import java.util.*
import javafx.application.Platform
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import net.corda.demobench.ui.CloseableTab
import tornadofx.*

class DemoBenchView : View("Corda Demo Bench") {

    override val root by fxml<Parent>()

    val addNodeButton by fxid<Button>()
    val nodeTabPane by fxid<TabPane>()

    init {
        importStylesheet("/net/corda/demobench/style.css")

        primaryStage.setOnCloseRequest {
            log.info("Exiting")

            closeAllTabs()
            Platform.exit()
        }

        addNodeButton.setOnAction {
            val nodeTab = createNodeTab()
            nodeTabPane.selectionModel.select(nodeTab)

            // Prevent us from creating new nodes until we have created the Network Map
            addNodeButton.isDisable = true
        }
        addNodeButton.fire()
    }

    private fun closeAllTabs() {
        ArrayList<Tab>(nodeTabPane.tabs).forEach {
            (it as CloseableTab).requestClose()
        }
    }

    fun createNodeTab(): CloseableTab {
        val nodeTabView = find<NodeTabView>()
        val nodeTab = nodeTabView.nodeTab

        nodeTabPane.tabs.add(nodeTab)
        return nodeTab
    }

    fun enableAddNodes() {
        addNodeButton.isDisable = false
    }
}
