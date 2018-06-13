package net.corda.bootstrapper.gui

import com.microsoft.azure.management.resources.fluentcore.arm.Region
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.transformation.SortedList
import javafx.event.EventHandler
import javafx.scene.control.ChoiceDialog
import javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY
import javafx.scene.control.TextInputDialog
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Priority
import javafx.stage.DirectoryChooser
import net.corda.bootstrapper.Constants
import net.corda.bootstrapper.GuiUtils
import net.corda.bootstrapper.NetworkBuilder
import net.corda.bootstrapper.backends.Backend
import net.corda.bootstrapper.context.Context
import net.corda.bootstrapper.nodes.*
import net.corda.bootstrapper.notaries.NotaryFinder
import org.apache.commons.lang3.RandomStringUtils
import tornadofx.*
import java.io.File
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.collections.ArrayList

class BootstrapperView : View("Network Bootstrapper") {

    val YAML_MAPPER = Constants.getContextMapper()


    val controller: State by inject()

    val textarea = textarea {
        maxWidth = Double.MAX_VALUE
        maxHeight = Double.MAX_VALUE
    }

    override val root = vbox {

        menubar {
            menu("File") {
                item("Open") {
                    action {
                        selectNodeDirectory().thenAcceptAsync({ (notaries: List<FoundNode>, nodes: List<FoundNode>) ->
                            controller.nodes(nodes)
                            controller.notaries(notaries)
                        })
                    }
                }

                item("Build") {
                    enableWhen(controller.baseDir.isNotNull)
                    action {
                        controller.clear()
                        val availableBackends = getAvailableBackends()
                        val backend = ChoiceDialog<Backend.BackendType>(availableBackends.first(), availableBackends).showAndWait()
                        var networkName = "gui-network"
                        backend.ifPresent { selectedBackEnd ->

                            val backendParams = when (selectedBackEnd) {
                                Backend.BackendType.LOCAL_DOCKER -> {

                                    emptyMap<String, String>()
                                }
                                Backend.BackendType.AZURE -> {
                                    val defaultName = RandomStringUtils.randomAlphabetic(4) + "-network"
                                    val textInputDialog = TextInputDialog(defaultName)
                                    textInputDialog.title = "Choose Network Name"
                                    networkName = textInputDialog.showAndWait().orElseGet { defaultName }
                                    mapOf(Constants.REGION_ARG_NAME to ChoiceDialog<Region>(Region.EUROPE_WEST, Region.values().toList().sortedBy { it.name() }).showAndWait().get().name())
                                }
                            }

                            val nodeCount = controller.foundNodes.map { it.id to it.count }.toMap()
                            val result = NetworkBuilder.instance()
                                    .withBasedir(controller.baseDir.get())
                                    .withNetworkName(networkName)
                                    .onNodeBuild(controller::addBuiltNode)
                                    .onNodePushed(controller::addPushedNode)
                                    .onNodeInstance(controller::addInstance)
                                    .withBackend(selectedBackEnd)
                                    .withNodeCounts(nodeCount)
                                    .withBackendOptions(backendParams)
                                    .build()
                            result.handle { v, t ->
                                runLater {
                                    if (t != null) {
                                        GuiUtils.showException("Failed to build network", "Failure due to", t)
                                    } else {
                                        controller.networkContext.set(v.second)
                                    }
                                }
                            }
                        }
                    }
                }

                item("Add Node") {
                    enableWhen(controller.networkContext.isNotNull)
                    action {
                        val foundNodes = controller.foundNodes.map { it.id }
                        val nodeToAdd = ChoiceDialog<String>(foundNodes.first(), *foundNodes.toTypedArray()).showAndWait()
                        val context = controller.networkContext.value
                        nodeToAdd.ifPresent { node ->
                            runLater {
                                val (_, instantiator, _) = Backend.fromContext(
                                        context,
                                        File(controller.baseDir.get(), Constants.BOOTSTRAPPER_DIR_NAME))
                                val nodeAdder = NodeAdder(context, NodeInstantiator(instantiator, context))
                                nodeAdder.addNode(context, node).handleAsync { instanceInfo, t ->
                                    t?.let {
                                        GuiUtils.showException("Failed", "Failed to add node", it)
                                    }
                                    instanceInfo?.let {
                                        runLater {
                                            controller.addInstance(NodeInstanceTableEntry(
                                                    it.groupId,
                                                    it.instanceName,
                                                    it.instanceAddress,
                                                    it.reachableAddress,
                                                    it.portMapping[Constants.NODE_P2P_PORT] ?: Constants.NODE_P2P_PORT,
                                                    it.portMapping[Constants.NODE_SSHD_PORT]
                                                            ?: Constants.NODE_SSHD_PORT))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        hbox {
            vbox {
                label("Nodes to build")
                val foundNodesTable = tableview(controller.foundNodes) {
                    readonlyColumn("ID", FoundNodeTableEntry::id)
                    column("Count", FoundNodeTableEntry::count).makeEditable()
                    vgrow = Priority.ALWAYS
                    hgrow = Priority.ALWAYS
                }
                foundNodesTable.columnResizePolicy = CONSTRAINED_RESIZE_POLICY
                label("Notaries to build")
                val notaryListView = listview(controller.foundNotaries) {
                    vgrow = Priority.ALWAYS
                    hgrow = Priority.ALWAYS
                }
                notaryListView.cellFormat { text = it.name }
                vgrow = Priority.ALWAYS
                hgrow = Priority.ALWAYS
            }

            vbox {

                label("Built Nodes")
                tableview(controller.builtNodes) {
                    readonlyColumn("ID", BuiltNodeTableEntry::id)
                    readonlyColumn("LocalImageId", BuiltNodeTableEntry::localImageId)
                    columnResizePolicy = CONSTRAINED_RESIZE_POLICY
                    vgrow = Priority.ALWAYS
                    hgrow = Priority.ALWAYS
                }


                label("Pushed Nodes")
                tableview(controller.pushedNodes) {
                    readonlyColumn("ID", PushedNode::name)
                    readonlyColumn("RemoteImageId", PushedNode::remoteImageName)
                    columnResizePolicy = CONSTRAINED_RESIZE_POLICY
                    vgrow = Priority.ALWAYS
                    hgrow = Priority.ALWAYS
                }
                vgrow = Priority.ALWAYS
                hgrow = Priority.ALWAYS
            }

            borderpane {
                top = vbox {
                    label("Instances")
                    tableview(controller.nodeInstances) {
                        onMouseClicked = EventHandler<MouseEvent> { _ ->
                            textarea.text = YAML_MAPPER.writeValueAsString(selectionModel.selectedItem)
                        }
                        readonlyColumn("ID", NodeInstanceTableEntry::id)
                        readonlyColumn("InstanceId", NodeInstanceTableEntry::nodeInstanceName)
                        readonlyColumn("Address", NodeInstanceTableEntry::address)
                        columnResizePolicy = CONSTRAINED_RESIZE_POLICY
                    }
                }
                center = textarea
                vgrow = Priority.ALWAYS
                hgrow = Priority.ALWAYS
            }

            vgrow = Priority.ALWAYS
            hgrow = Priority.ALWAYS
        }

    }

    private fun getAvailableBackends(): List<Backend.BackendType> {
        return Backend.BackendType.values().toMutableList();
    }


    fun selectNodeDirectory(): CompletableFuture<Pair<List<FoundNode>, List<FoundNode>>> {
        val fileChooser = DirectoryChooser();
        fileChooser.initialDirectory = File(System.getProperty("user.home"))
        val file = fileChooser.showDialog(null)
        controller.baseDir.set(file)
        return processSelectedDirectory(file)
    }


    fun processSelectedDirectory(dir: File): CompletableFuture<Pair<List<FoundNode>, List<FoundNode>>> {
        val foundNodes = CompletableFuture.supplyAsync {
            val nodeFinder = NodeFinder(dir)
            nodeFinder.findNodes()
        }
        val foundNotaries = CompletableFuture.supplyAsync {
            val notaryFinder = NotaryFinder(dir)
            notaryFinder.findNotaries()
        }
        return foundNodes.thenCombine(foundNotaries) { nodes, notaries ->
            notaries to nodes
        }
    }
}

class State : Controller() {

    val foundNodes = Collections.synchronizedList(ArrayList<FoundNodeTableEntry>()).observable()
    val builtNodes = Collections.synchronizedList(ArrayList<BuiltNodeTableEntry>()).observable()
    val pushedNodes = Collections.synchronizedList(ArrayList<PushedNode>()).observable()

    private val backingUnsortedInstances = Collections.synchronizedList(ArrayList<NodeInstanceTableEntry>()).observable()
    val nodeInstances = SortedList(backingUnsortedInstances, COMPARATOR)

    val foundNotaries = Collections.synchronizedList(ArrayList<FoundNode>()).observable()
    val networkContext = SimpleObjectProperty<Context>(null)

    fun clear() {
        builtNodes.clear()
        pushedNodes.clear()
        backingUnsortedInstances.clear()
        networkContext.set(null)
    }

    fun nodes(nodes: List<FoundNode>) {
        foundNodes.clear()
        nodes.forEach { addFoundNode(it) }
    }

    fun notaries(notaries: List<FoundNode>) {
        foundNotaries.clear()
        notaries.forEach { runLater { foundNotaries.add(it) } }
    }

    var baseDir = SimpleObjectProperty<File>(null)


    fun addFoundNode(foundNode: FoundNode) {
        runLater {
            foundNodes.add(FoundNodeTableEntry(foundNode.name))
        }
    }

    fun addBuiltNode(builtNode: BuiltNode) {
        runLater {
            builtNodes.add(BuiltNodeTableEntry(builtNode.name, builtNode.localImageId))
        }
    }

    fun addPushedNode(pushedNode: PushedNode) {
        runLater {
            pushedNodes.add(pushedNode)
        }
    }

    fun addInstance(nodeInstance: NodeInstance) {
        runLater {
            backingUnsortedInstances.add(NodeInstanceTableEntry(
                    nodeInstance.name,
                    nodeInstance.nodeInstanceName,
                    nodeInstance.expectedFqName,
                    nodeInstance.reachableAddress,
                    nodeInstance.portMapping[Constants.NODE_P2P_PORT] ?: Constants.NODE_P2P_PORT,
                    nodeInstance.portMapping[Constants.NODE_SSHD_PORT] ?: Constants.NODE_SSHD_PORT))
        }
    }

    fun addInstance(nodeInstance: NodeInstanceTableEntry) {
        runLater {
            backingUnsortedInstances.add(nodeInstance)
        }
    }

    companion object {
        val COMPARATOR: (NodeInstanceTableEntry, NodeInstanceTableEntry) -> Int = { o1, o2 ->
            if (o1.id == (o2.id)) {
                o1.nodeInstanceName.compareTo(o2.nodeInstanceName)
            } else {
                o1.id.compareTo(o2.id)
            }
        }
    }


}

data class FoundNodeTableEntry(val id: String,
                               @Volatile var count: Int = 1)

data class BuiltNodeTableEntry(val id: String, val localImageId: String)

data class NodeInstanceTableEntry(val id: String,
                                  val nodeInstanceName: String,
                                  val address: String,
                                  val locallyReachableAddress: String,
                                  val rpcPort: Int,
                                  val sshPort: Int)