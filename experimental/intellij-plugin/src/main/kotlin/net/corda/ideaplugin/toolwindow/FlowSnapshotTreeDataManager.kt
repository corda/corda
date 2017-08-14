package net.corda.ideaplugin.toolwindow

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.icons.AllIcons
import java.io.File
import javax.swing.Icon
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.MutableTreeNode

/**
 * Snapshot tree data descriptor. It is used as userObject in the [DefaultMutableTreeNode] class.
 */
class SnapshotDataDescriptor(val data: Any?, val icon: Icon, val key: String? = null) {
    override fun toString(): String = data?.toString() ?: "null"
}

/**
 * Manager class for flow snapshots. It is responsible for parsing data read from a snapshot file and constructing
 * tree model from it.
 */
class FlowSnapshotTreeDataManager(tree: JTree) {
    companion object {
        const val SNAPSHOT_FILE_PREFIX = "flowStackSnapshot"
    }

    // Root node for the snapshot hierarchy, which is an empty node.
    private val root = DefaultMutableTreeNode(SnapshotDataDescriptor(null, AllIcons.Json.Object))
    // Snapshot tree model
    private val snapshotModel = DefaultTreeModel(root)

    private val mapper = ObjectMapper()

    init {
        tree.model = snapshotModel
        snapshotModel.reload()
    }

    /**
     * Removes all current data from the snapshot hierarchy and refreshes the model.
     */
    fun clear() {
        root.removeAllChildren()
        snapshotModel.reload()
    }

    /**
     * Constructs tree model from snapshot files
     */
    fun loadSnapshots(snapshots: List<File>) {
        root.removeAllChildren()
        snapshots.forEach {
            insertNodeToSnapshotModel(it)
        }
        snapshotModel.reload()
    }

    /**
     * Adds snapshot file to the snapshot hierarchy. The content of the file is processed and
     * the model is updated accordingly.
     */
    fun addNodeToSnapshotModel(snapshotFile: File) {
        val insertionIndex = -(root.childNodes().map {
            (it.userObject as SnapshotDataDescriptor).key
        }.binarySearch(extractFileName(snapshotFile))) - 1
        insertNodeToSnapshotModel(snapshotFile, insertionIndex)
    }

    /**
     * Removes the snapshot file from the snapshot hierarchy. The model is also updated after this operation.
     */
    fun removeNodeFromSnapshotModel(snapshotFile: File) {
        val node = root.childNodes().find {
            (it.userObject as SnapshotDataDescriptor).data == extractFileName(snapshotFile)
        } as MutableTreeNode?
        if (node != null) {
            snapshotModel.removeNodeFromParent(node)
            snapshotModel.nodesWereRemoved(root, intArrayOf(), arrayOf(node))
            return
        }
    }

    private fun insertNodeToSnapshotModel(snapshotFile: File, insertionIndex: Int = -1) {
        buildChildrenModel(mapper.readTree(snapshotFile), root, extractFileName(snapshotFile), insertionIndex)
    }

    private fun extractFileName(file: File): String {
        return file.name.substring(0, file.name.lastIndexOf("."))
    }

    private fun buildChildrenModel(
            node: JsonNode?,
            parent: DefaultMutableTreeNode, key: String? = null,
            insertionIndex: Int = -1) {
        val child: DefaultMutableTreeNode
        if (node == null || !node.isContainerNode) {
            child = DefaultMutableTreeNode(SnapshotDataDescriptor(node, AllIcons.Debugger.Db_primitive, key))
            addToModelAndRefresh(snapshotModel, child, parent, insertionIndex)
        } else {
            if (node.isArray) {
                child = DefaultMutableTreeNode(SnapshotDataDescriptor(key, AllIcons.Debugger.Db_array))
                addToModelAndRefresh(snapshotModel, child, parent, insertionIndex)
                node.mapIndexed { index: Int, item: JsonNode? ->
                    buildChildrenModel(item, child, index.toString())
                }
            } else {
                child = DefaultMutableTreeNode(SnapshotDataDescriptor(key, AllIcons.Json.Object))
                addToModelAndRefresh(snapshotModel, child, parent, insertionIndex)
                node.fields().forEach {
                    buildChildrenModel(it.value, child, it.key)
                }
            }
        }
    }
}