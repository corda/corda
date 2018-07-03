package net.corda.bootstrapper.gui

import com.microsoft.azure.management.resources.fluentcore.arm.Region
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.ObservableListBase
import javafx.collections.transformation.SortedList
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.DirectoryChooser
import net.corda.bootstrapper.Constants
import net.corda.bootstrapper.GuiUtils
import net.corda.bootstrapper.NetworkBuilder
import net.corda.bootstrapper.backends.Backend
import net.corda.bootstrapper.context.Context
import net.corda.bootstrapper.nodes.*
import net.corda.bootstrapper.notaries.NotaryFinder
import org.apache.commons.lang3.RandomStringUtils
import org.controlsfx.control.SegmentedButton
import tornadofx.*
import java.io.File
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import kotlin.Comparator
import kotlin.collections.ArrayList

class BootstrapperView : View("Corda Network Builder") {
    val YAML_MAPPER = Constants.getContextMapper()
    override val root: VBox by fxml("/views/mainPane.fxml")

    val controller: State by inject()

    val localDockerBtn: ToggleButton by fxid()
    val azureBtn: ToggleButton by fxid()
    val nodeTableView: TableView<NodeTemplateInfo> by fxid()
    val templateChoiceBox: ChoiceBox<String> by fxid()
    val buildButton: Button by fxid()
    val addInstanceButton: Button by fxid()
    val infoTextArea: TextArea by fxid()

    init {
        visuallyTweakBackendSelector()

        buildButton.run {
            enableWhen { controller.baseDir.isNotNull }
            action {
                var networkName = "corda-network"

                val selectedBackEnd = when {
                    azureBtn.isSelected -> Backend.BackendType.AZURE
                    localDockerBtn.isSelected -> Backend.BackendType.LOCAL_DOCKER
                    else -> kotlin.error("Unknown backend selected")
                }

                val backendParams = when (selectedBackEnd) {
                    Backend.BackendType.LOCAL_DOCKER -> {
                        emptyMap()
                    }
                    Backend.BackendType.AZURE -> {
                        val pair = setupAzureRegionOptions()
                        networkName = pair.second
                        pair.first
                    }
                }

                val nodeCount = controller.foundNodes.map { it.id to it.count }.toMap()
                val result = NetworkBuilder.instance()
                        .withBasedir(controller.baseDir.get())
                        .withNetworkName(networkName)
                        .onNodeStartBuild(controller::onBuild)
                        .onNodeBuild(controller::addBuiltNode)
                        .onNodePushStart(controller::addBuiltNode)
                        .onNodePushed(controller::addPushedNode)
                        .onNodeInstancesRequested(controller::addInstanceRequests)
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

        templateChoiceBox.run {
            enableWhen { controller.networkContext.isNotNull }
            controller.networkContext.addListener { _, _, newValue ->
                if (newValue != null) {
                    items = object : ObservableListBase<String>() {
                        override fun get(index: Int): String {
                            return controller.foundNodes[index].id
                        }

                        override val size: Int
                            get() = controller.foundNodes.size
                    }
                    selectionModel.select(controller.foundNodes[0].id)
                }
            }
        }

        addInstanceButton.run {
            enableWhen { controller.networkContext.isNotNull }
            action {
                templateChoiceBox.selectionModel.selectedItem?.let { nodeToAdd ->
                    val context = controller.networkContext.value
                    runLater {
                        val (_, instantiator, _) = Backend.fromContext(
                                context,
                                File(controller.baseDir.get(), Constants.BOOTSTRAPPER_DIR_NAME))
                        val nodeAdder = NodeAdder(context, NodeInstantiator(instantiator, context))
                        controller.addInstanceRequest(nodeToAdd)
                        nodeAdder.addNode(context, nodeToAdd).handleAsync { instanceInfo, t ->
                            t?.let {
                                GuiUtils.showException("Failed", "Failed to add node", it)
                            }
                            instanceInfo?.let {
                                runLater {
                                    controller.addInstance(NodeInstanceEntry(
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

        nodeTableView.run {
            items = controller.sortedNodes
            column("ID", NodeTemplateInfo::templateId)
            column("Type", NodeTemplateInfo::nodeType)
            column("Local Docker Image", NodeTemplateInfo::localDockerImageId)
            column("Repository Image", NodeTemplateInfo::repositoryImageId)
            column("Status", NodeTemplateInfo::status)
            columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
            hgrow = Priority.ALWAYS

            onMouseClicked = EventHandler<MouseEvent> { _ ->
                val selectedItem: NodeTemplateInfo = selectionModel.selectedItem ?: return@EventHandler
                infoTextArea.text = YAML_MAPPER.writeValueAsString(translateForPrinting(selectedItem))
            }
        }
    }

    private fun visuallyTweakBackendSelector() {
        // The SegmentedButton will jam together the two toggle buttons in a way
        // that looks more modern.
        val hBox = localDockerBtn.parent as HBox
        val idx = hBox.children.indexOf(localDockerBtn)
        // Adding this to the hbox will re-parent the two toggle buttons into the
        // SegmentedButton control, so we have to put it in the same position as
        // the original buttons. Unfortunately it's not so Scene Builder friendly.
        hBox.children.add(idx, SegmentedButton(localDockerBtn, azureBtn).apply {
            styleClass.add(SegmentedButton.STYLE_CLASS_DARK)
        })
    }

    private fun setupAzureRegionOptions(): Pair<Map<String, String>, String> {
        var networkName1 = RandomStringUtils.randomAlphabetic(4) + "-network"
        val textInputDialog = TextInputDialog(networkName1)
        textInputDialog.title = "Azure Resource Group"
        networkName1 = textInputDialog.showAndWait().orElseGet { networkName1 }
        return Pair(mapOf(Constants.REGION_ARG_NAME to ChoiceDialog<Region>(Region.EUROPE_WEST, Region.values().toList().sortedBy { it.name() }).showAndWait().get().name()), networkName1)
    }

    private fun translateForPrinting(selectedItem: NodeTemplateInfo): Any {
        return object {
            val templateId = selectedItem.templateId.get()
            val nodeType = selectedItem.nodeType.get()
            val localDockerImageId = selectedItem.localDockerImageId.get()
            val repositoryImageId = selectedItem.repositoryImageId.get()
            val status = selectedItem.status.get()
            val instances = selectedItem.instances.map { it }
        }
    }

    @FXML
    fun onOpenClicked() {
        val chooser = DirectoryChooser()
        chooser.initialDirectory = File(System.getProperty("user.home"))
        val file: File = chooser.showDialog(null) ?: return   // Null means user cancelled.
        processSelectedDirectory(file)
    }

    private fun processSelectedDirectory(dir: File) {
        controller.clearAll()
        controller.baseDir.set(dir)
        val foundNodes = CompletableFuture.supplyAsync {
            val nodeFinder = NodeFinder(dir)
            nodeFinder.findNodes()
        }
        val foundNotaries = CompletableFuture.supplyAsync {
            val notaryFinder = NotaryFinder(dir)
            notaryFinder.findNotaries()
        }
        foundNodes.thenCombine(foundNotaries) { nodes, notaries ->
            notaries to nodes
        }.thenAcceptAsync({ (notaries: List<FoundNode>, nodes: List<FoundNode>) ->
            runLater {
                controller.foundNodes(nodes)
                controller.notaries(notaries)
            }
        })
    }

    class NodeTemplateInfo(templateId: String, type: NodeType) {
        val templateId: SimpleStringProperty = object : SimpleStringProperty(templateId) {
            override fun toString(): String {
                return this.get()?.toString() ?: "null"
            }
        }
        val nodeType: SimpleObjectProperty<NodeType> = SimpleObjectProperty(type)
        val localDockerImageId: SimpleStringProperty = SimpleStringProperty()
        val repositoryImageId: SimpleStringProperty = SimpleStringProperty()
        val status: SimpleObjectProperty<NodeBuildStatus> = SimpleObjectProperty(NodeBuildStatus.DISCOVERED)
        val instances: MutableList<NodeInstanceEntry> = ArrayList()
        val numberOfInstancesWaiting: AtomicInteger = AtomicInteger(-1)
    }

    enum class NodeBuildStatus {
        DISCOVERED, LOCALLY_BUILDING, LOCALLY_BUILT, REMOTE_PUSHING, REMOTE_PUSHED, INSTANTIATING, INSTANTIATED,
    }

    enum class NodeType {
        NODE, NOTARY
    }

    class State : Controller() {
        val foundNodes = Collections.synchronizedList(ArrayList<FoundNodeTableEntry>()).observable()
        val foundNotaries = Collections.synchronizedList(ArrayList<FoundNode>()).observable()
        val networkContext = SimpleObjectProperty<Context>(null)

        val unsortedNodes = Collections.synchronizedList(ArrayList<NodeTemplateInfo>()).observable()
        val sortedNodes = SortedList(unsortedNodes, Comparator<NodeTemplateInfo> { o1, o2 ->
            compareValues(o1.nodeType.toString() + o1.templateId, o2.nodeType.toString() + o2.templateId) * -1
        })

        fun clear() {
            networkContext.set(null)
        }

        fun clearAll() {
            networkContext.set(null)
            foundNodes.clear()
            foundNotaries.clear()
            unsortedNodes.clear()
        }

        fun foundNodes(nodesToAdd: List<FoundNode>) {
            foundNodes.clear()
            nodesToAdd.forEach {
                runLater {
                    foundNodes.add(FoundNodeTableEntry(it.name))
                    unsortedNodes.add(NodeTemplateInfo(it.name, NodeType.NODE))
                }
            }
        }

        fun notaries(notaries: List<FoundNode>) {
            foundNotaries.clear()
            notaries.forEach {
                runLater {
                    foundNotaries.add(it)
                    unsortedNodes.add(NodeTemplateInfo(it.name, NodeType.NOTARY))
                }
            }

        }

        var baseDir = SimpleObjectProperty<File>(null)

        fun addBuiltNode(builtNode: BuiltNode) {
            runLater {
                val foundNode = unsortedNodes.find { it.templateId.get() == builtNode.name }
                foundNode?.status?.set(NodeBuildStatus.LOCALLY_BUILT)
                foundNode?.localDockerImageId?.set(builtNode.localImageId)
            }
        }

        fun addPushedNode(pushedNode: PushedNode) {
            runLater {
                val foundNode = unsortedNodes.find { it.templateId.get() == pushedNode.name }
                foundNode?.status?.set(NodeBuildStatus.REMOTE_PUSHED)
                foundNode?.repositoryImageId?.set(pushedNode.remoteImageName)

            }
        }

        fun onBuild(nodeBuilding: FoundNode) {
            val foundNode = unsortedNodes.find { it.templateId.get() == nodeBuilding.name }
            foundNode?.status?.set(NodeBuildStatus.LOCALLY_BUILDING)
        }

        fun addInstance(nodeInstance: NodeInstance) {
            addInstance(NodeInstanceEntry(
                    nodeInstance.name,
                    nodeInstance.nodeInstanceName,
                    nodeInstance.expectedFqName,
                    nodeInstance.reachableAddress,
                    nodeInstance.portMapping[Constants.NODE_P2P_PORT] ?: Constants.NODE_P2P_PORT,
                    nodeInstance.portMapping[Constants.NODE_SSHD_PORT] ?: Constants.NODE_SSHD_PORT)
            )
        }

        fun addInstanceRequests(requests: List<NodeInstanceRequest>) {
            requests.firstOrNull()?.let { request ->
                unsortedNodes.find { it.templateId.get() == request.name }?.let {
                    it.numberOfInstancesWaiting.set(requests.size)
                    it.status.set(NodeBuildStatus.INSTANTIATING)
                }
            }
        }

        fun addInstance(nodeInstance: NodeInstanceEntry) {
            runLater {
                val foundNode = unsortedNodes.find { it.templateId.get() == nodeInstance.id }
                foundNode?.instances?.add(nodeInstance)
                if (foundNode != null && foundNode.instances.size == foundNode.numberOfInstancesWaiting.get()) {
                    foundNode.status.set(NodeBuildStatus.INSTANTIATED)
                }
            }
        }

        fun addInstanceRequest(nodeToAdd: String) {
            val foundNode = unsortedNodes.find { it.templateId.get() == nodeToAdd }
            foundNode?.numberOfInstancesWaiting?.incrementAndGet()
            foundNode?.status?.set(NodeBuildStatus.INSTANTIATING)
        }
    }

    data class NodeInstanceEntry(val id: String,
                                 val nodeInstanceName: String,
                                 val address: String,
                                 val locallyReachableAddress: String,
                                 val rpcPort: Int,
                                 val sshPort: Int)

}

data class FoundNodeTableEntry(val id: String, @Volatile var count: Int = 1)