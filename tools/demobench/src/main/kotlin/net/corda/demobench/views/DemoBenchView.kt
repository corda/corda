package net.corda.demobench.views

import javafx.application.Platform
import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.MenuItem
import javafx.scene.control.Tab
import javafx.scene.control.TabPane
import net.corda.demobench.model.InstallConfig
import net.corda.demobench.model.NodeController
import net.corda.demobench.profile.ProfileController
import net.corda.demobench.ui.CloseableTab
import org.controlsfx.dialog.ExceptionDialog
import tornadofx.*
import java.util.*

class DemoBenchView : View("Corda DemoBench") {
    override val root by fxml<Parent>()

    private val profileController by inject<ProfileController>()
    private val nodeController by inject<NodeController>()
    private val addNodeButton by fxid<Button>()
    private val nodeTabPane by fxid<TabPane>()
    private val menuOpen by fxid<MenuItem>()
    private val menuSaveAs by fxid<MenuItem>()

    init {
        importStylesheet("/net/corda/demobench/r3-style.css")
        importStylesheet("/net/corda/demobench/style.css")

        configureShutdown()

        configureProfileSaveAs()
        configureProfileOpen()

        configureAddNode()
    }

    private fun configureShutdown() = primaryStage.setOnCloseRequest {
        log.info("Exiting")

        // Prevent any new NodeTabViews from being created.
        addNodeButton.isDisable = true

        closeAllTabs()
        Platform.exit()
    }

    private fun configureProfileSaveAs() = menuSaveAs.setOnAction {
        try {
            if (profileController.saveProfile()) {
                menuSaveAs.isDisable = true
            }
        } catch (e: Exception) {
            ExceptionDialog(e).apply { initOwner(root.scene.window) }.showAndWait()
        }
    }

    private fun configureProfileOpen() = menuOpen.setOnAction {
        try {
            val profile = profileController.openProfile() ?: return@setOnAction
            loadProfile(profile)
        } catch (e: Exception) {
            ExceptionDialog(e).apply { initOwner(root.scene.window) }.showAndWait()
        }
    }

    private fun configureAddNode() {
        addNodeButton.setOnAction {
            val nodeTabView = createNodeTabView(true)
            nodeTabPane.selectionModel.select(nodeTabView.nodeTab)

            // Prevent us from creating new nodes until we have created the Network Map
            addNodeButton.isDisable = true
        }
        addNodeButton.fire()
    }

    private fun closeAllTabs() = ArrayList<Tab>(nodeTabPane.tabs).forEach {
        (it as CloseableTab).requestClose()
    }

    private fun createNodeTabView(showConfig: Boolean): NodeTabView {
        val nodeTabView = find<NodeTabView>(mapOf("showConfig" to showConfig))
        nodeTabPane.tabs.add(nodeTabView.nodeTab)
        return nodeTabView
    }

    private fun loadProfile(nodes: List<InstallConfig>) {
        closeAllTabs()
        nodeController.reset()

        nodes.forEach {
            val nodeTabView = createNodeTabView(false)
            nodeTabView.launch(nodeController.install(it))
        }

        enableAddNodes()
    }

    /**
     * Enable the "save profile" menu item.
     */
    fun enableSaveProfile() {
        menuSaveAs.isDisable = false
    }

    /**
     * Enables the button that allows us to create a new node.
     */
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
