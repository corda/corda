package net.corda.demobench.views

import java.util.*
import javafx.application.Platform
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.MenuItem
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import net.corda.demobench.model.NodeConfig
import net.corda.demobench.model.NodeController
import net.corda.demobench.profile.ProfileController
import net.corda.demobench.ui.CloseableTab
import org.controlsfx.dialog.ExceptionDialog
import tornadofx.*

class DemoBenchView : View("Corda Demo Bench") {

    override val root by fxml<Parent>()

    private val profileController by inject<ProfileController>()
    private val nodeController by inject<NodeController>()
    private val addNodeButton by fxid<Button>()
    private val nodeTabPane by fxid<TabPane>()
    private val menuOpen by fxid<MenuItem>()
    private val menuSave by fxid<MenuItem>()
    private val menuSaveAs by fxid<MenuItem>()

    init {
        importStylesheet("/net/corda/demobench/style.css")

        primaryStage.setOnCloseRequest {
            log.info("Exiting")

            // Prevent any new NodeTabViews from being created.
            addNodeButton.isDisable = true

            closeAllTabs()
            Platform.exit()
        }

        menuSaveAs.setOnAction {
            profileController.saveAs()
        }
        menuSave.setOnAction {
            profileController.save()
        }
        menuOpen.setOnAction {
            try {
                val profile = profileController.openProfile()
                if (profile != null) {
                    loadProfile(profile)
                }
            } catch (e: Exception) {
                ExceptionDialog(e).apply { initOwner(root.scene.window) }.showAndWait()
            }
        }

        addNodeButton.setOnAction {
            val nodeTabView = createNodeTabView(true)
            nodeTabPane.selectionModel.select(nodeTabView.nodeTab)

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

    fun createNodeTabView(showConfig: Boolean): NodeTabView {
        val nodeTabView = find<NodeTabView>(mapOf("showConfig" to showConfig))
        nodeTabPane.tabs.add(nodeTabView.nodeTab)
        return nodeTabView
    }

    fun loadProfile(nodes: List<NodeConfig>) {
        closeAllTabs()
        nodeController.reset()

        nodes.forEach {
            val nodeTabView = createNodeTabView(false)
            nodeTabView.launch(nodeController.relocate(it))
        }

        enableAddNodes()
    }

    fun enableAddNodes() {
        addNodeButton.isDisable = false
    }

    /**
     * Ensures that DemoBench always has at least one instance NodeTabView.
     * This method must NOT be called if DemoBench is shutting down.
     */
    fun forceAtLeastOneTab() {
        if (nodeTabPane.tabs.isEmpty()) {
            addNodeButton.fire()
        }
    }
}
