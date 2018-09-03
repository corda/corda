package net.corda.ideaplugin.toolwindow

import java.io.File
import java.nio.file.*
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import javax.swing.JTree
import javax.swing.SwingWorker
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Manager class for flow list data.
 */
class FlowTreeDataManager(val tree: JTree, val snapshotModel: FlowSnapshotTreeDataManager) {
    companion object {
        const val FLOWS_DIRECTORY = "flowStackSnapshots"
    }

    // Root node (i.e. [FLOWS_DIRECTORY]) of the flow directory hierarchy.
    private val root = DefaultMutableTreeNode()
    // Flow tree model
    private val flowModel = DefaultTreeModel(root)

    // Watcher service used to monitor directory contents addition and deletion.
    private val watcher: WatchService = FileSystems.getDefault().newWatchService()
    // Registered watch keys for the corresponding directories
    private val watchKeys = hashMapOf<WatchKey, File>()
    // Watch keys polling thread. Responsible for processing changes ot the contents of the monitored directories
    private val dirObserver: DirObserver = DirObserver()

    init {
        tree.model = flowModel
        flowModel.reload()
        dirObserver.execute()
    }

    /**
     * Builds the flow directory hierarchy with the root being associated with the passed [flowsDirectory].
     * If the parameter is missing the function rebuilds current hierarchy and reloads (refreshes) current model.
     */
    fun loadFlows(flowsDirectory: File? = root.file) {
        root.userObject = flowsDirectory ?: return
        root.removeAllChildren()

        // Invalidate all current watch keys
        invalidateWatchKeys()

        // We need to add a watch key to the parent
        startWatching(flowsDirectory)

        // We expect 2-level directory nesting. The first level are dates and the second level are flow IDs.
        // Dates directories
        flowsDirectory.listFiles().filter { it.isDirectory }.forEach {
            insertDateDirectory(it)
        }
        flowModel.reload()
    }

    /*
     * Inserts date directory to the node hierarchy. A date directory is considered to be on the first hierarchy level.
     */
    private fun insertDateDirectory(dateDir: File, insertionIndex: Int = -1) {
        startWatching(dateDir)
        val dateNode = DefaultMutableTreeNode(dateDir)
        addToModelAndRefresh(flowModel, dateNode, root, insertionIndex)
        // Flows directories
        dateDir.listFiles().filter { it.isDirectory }.forEach {
            startWatching(it)
            addToModelAndRefresh(flowModel, DefaultMutableTreeNode(it), dateNode)
        }
    }

    /**
     * Removes current hierarchy.
     */
    fun clear() {
        invalidateWatchKeys()
        root.removeAllChildren()
        flowModel.reload()
    }

    private fun isSelected(dir: File?): Boolean {
        val node = tree.selectedNode
        return node != null && dir == node.file
    }

    private fun startWatching(dir: File) {
        val key = dir.toPath().register(watcher, ENTRY_CREATE, ENTRY_DELETE)
        watchKeys.put(key, dir)
    }

    private fun invalidateWatchKeys() {
        watchKeys.keys.forEach { it.cancel() }
        watchKeys.clear()
    }

    private fun addNodeToFlowModel(dir: File) {
        // We consider addition only of either a date or flow directory
        val parent = dir.parentFile
        if (parent.name == FLOWS_DIRECTORY) {
            // if a date directory has been added this means that that its parent is [FLOWS_DIRECTORY]
            insertDateDirectory(dir, findInsertionIndex(root.childNodes, dir.name))
        } else if (parent.parentFile.name == FLOWS_DIRECTORY) {
            // if a flow directory has been added this means that that the parent of its parent is [FLOWS_DIRECTORY]
            val parentNode = root.childNodes.findByFile(parent) ?: return
            flowModel.insertNodeInto(DefaultMutableTreeNode(dir), parentNode, findInsertionIndex(parentNode.childNodes, dir.name))
            startWatching(dir)
        }
    }

    private fun removeNodeFromFlowModel(dir: File) {
        val selectedNode = tree.selectedNode
        if (selectedNode != null && selectedNode.file == dir) {
            // Reload flows if the [dir] is currently selected
            loadFlows()
            return
        }
        val parent = dir.parentFile
        var parentNode: DefaultMutableTreeNode? = null
        if (parent.name == FLOWS_DIRECTORY) {
            // if a date directory has been added this means that that its parent is [FLOWS_DIRECTORY]
            parentNode = root
        } else if (parent.parentFile.name == FLOWS_DIRECTORY) {
            // if a flow directory has been added this means that that the parent of its parent is [FLOWS_DIRECTORY]
            parentNode = root.childNodes.findByFile(parent)
        }
        if (parentNode != null) {
            val node = parentNode.childNodes.findByFile(dir) ?: return
            flowModel.removeNodeFromParent(node)
        }
    }

    private fun findInsertionIndex(nodes: List<DefaultMutableTreeNode>, name: String): Int {
        return -nodes.toList().map { (it.userObject as File).name }.binarySearch(name) - 1
    }

    /*
     * Swing thread polling the watcher service for any changes (i.e. entry creation or deletion).
     * Once a change is detected the event is processed accordingly.
     */
    private inner class DirObserver : SwingWorker<Void, WatchKey>() {
        override fun doInBackground(): Void? {
            while (true) {
                // wait for key to be signaled
                val key = try {
                    watcher.take()
                } catch (x: InterruptedException) {
                    return null
                }

                publish(key)

                if (!key.reset()) {
                    watchKeys.remove(key)
                }
            }
        }

        override fun process(keys: MutableList<WatchKey>?) {
            keys?.forEach {
                processKey(it)
            }
        }

        private fun processKey(key: WatchKey) {
            key.pollEvents().forEach {
                val kind = it.kind()

                // The OVERFLOW event can occur regardless if events are lost or discarded.
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    return
                }
                processEvent(it as WatchEvent<*>, key)
            }
        }

        private fun processEvent(event: WatchEvent<*>, key: WatchKey) {
            val parent = getParent(key)
            val file = File(parent, (event.context() as Path).toString())
            when (event.kind()) {
                ENTRY_CREATE -> {
                    if (!file.isDirectory) {
                        // We only need to do anything if the flow snapshots are being currently examined
                        if (isSelected(parent) && file.name.startsWith(FlowSnapshotTreeDataManager.SNAPSHOT_FILE_PREFIX)) {
                            snapshotModel.addNodeToSnapshotModel(file)
                        }
                    } else {
                        addNodeToFlowModel(file)
                    }
                }
                ENTRY_DELETE -> {
                    // We only need to do anything if the flow snapshots are being currently examined
                    if (isSelected(parent) && file.name.startsWith(FlowSnapshotTreeDataManager.SNAPSHOT_FILE_PREFIX)) {
                        snapshotModel.removeNodeFromSnapshotModel(file)
                    } else {
                        removeNodeFromFlowModel(file)
                    }
                }
            }
        }

        private fun getParent(key: WatchKey): File? {
            return watchKeys[key]
        }
    }
}

internal val DefaultMutableTreeNode.file get() = userObject as? File
internal val DefaultMutableTreeNode.childNodes get() = children().toList().mapNotNull { it as? DefaultMutableTreeNode }
private val JTree.selectedNode get() = lastSelectedPathComponent as? DefaultMutableTreeNode
private fun List<DefaultMutableTreeNode>.findByFile(file: File) = find { it.file == file }

internal fun addToModelAndRefresh(model: DefaultTreeModel,
                                  child: DefaultMutableTreeNode,
                                  parent: DefaultMutableTreeNode,
                                  insertionIndex: Int = -1) {
    val indices = if (insertionIndex < 0) {
        parent.add(child)
        intArrayOf(parent.childCount - 1)
    } else {
        parent.insert(child, insertionIndex)
        intArrayOf(insertionIndex)
    }
    model.nodesWereInserted(parent, indices)
}