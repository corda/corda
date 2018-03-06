/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.ideaplugin.toolwindow

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.icons.AllIcons
import net.corda.core.flows.FlowStackSnapshot
import java.io.File
import javax.swing.Icon
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.MutableTreeNode

/**
 * Manager class for flow snapshots. It is responsible for parsing data read from a snapshot file and constructing
 * tree model from it.
 */
class FlowSnapshotTreeDataManager(tree: JTree) {
    companion object {
        const val SNAPSHOT_FILE_PREFIX = "flowStackSnapshot"
    }

    // Root node for the snapshot hierarchy, which is an empty node.
    private val root = DefaultMutableTreeNode(Descriptor())
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
        val insertionIndex = -(root.childNodes.map {
            it.file?.name
        }.binarySearch(extractFileName(snapshotFile))) - 1
        insertNodeToSnapshotModel(snapshotFile, insertionIndex)
    }

    /**
     * Removes the snapshot file from the snapshot hierarchy. The model is also updated after this operation.
     */
    fun removeNodeFromSnapshotModel(snapshotFile: File) {
        val snapshotFileName = extractFileName(snapshotFile)
        val node = root.childNodes.find {
            it.file?.name == snapshotFileName
        } as MutableTreeNode?
        if (node != null) {
            snapshotModel.removeNodeFromParent(node)
            snapshotModel.nodesWereRemoved(root, intArrayOf(), arrayOf(node))
            return
        }
    }

    private fun insertNodeToSnapshotModel(snapshotFile: File, insertionIndex: Int = -1) {
        val fileNode = DefaultMutableTreeNode(Descriptor(snapshotFile, AllIcons.FileTypes.Custom, snapshotFile.name))
        val snapshot = mapper.readValue(snapshotFile, FlowStackSnapshot::class.java)
        fileNode.add(DefaultMutableTreeNode(Descriptor(snapshot.time, AllIcons.Debugger.Db_primitive, "timestamp")))
        fileNode.add(DefaultMutableTreeNode(Descriptor(snapshot.flowClass, AllIcons.Debugger.Db_primitive, "flowClass")))
        val framesNode = DefaultMutableTreeNode(Descriptor(icon = AllIcons.Debugger.Db_array, label = "stackFrames"))
        fileNode.add(framesNode)
        snapshot.stackFrames.forEach {
            val ste = it.stackTraceElement!!
            val label = "${ste.className}.${ste.methodName}(line:${ste.lineNumber}) - ${ste.fileName}"
            val frameNode = DefaultMutableTreeNode(Descriptor(icon = AllIcons.Debugger.StackFrame, label = label))
            framesNode.add(frameNode)
            it.stackObjects.mapIndexed { index: Int, stackItem: Any? ->
                buildChildrenModel(mapper.convertValue(stackItem, JsonNode::class.java), frameNode, index.toString())
            }
        }

        addToModelAndRefresh(snapshotModel, fileNode, root, insertionIndex)
    }

    private fun extractFileName(file: File): String {
        return file.name.substring(0, file.name.lastIndexOf("."))
    }

    private fun buildChildrenModel(
            node: JsonNode?,
            parent: DefaultMutableTreeNode, label: String? = null) {
        val child: DefaultMutableTreeNode
        if (node == null || !node.isContainerNode) {
            parent.add(DefaultMutableTreeNode(Descriptor(node, AllIcons.Debugger.Db_primitive, label)))
        } else {
            if (node.isArray) {
                child = DefaultMutableTreeNode(Descriptor(icon = AllIcons.Debugger.Db_array, label = label))
                parent.add(child)
                node.mapIndexed { index: Int, item: JsonNode? ->
                    buildChildrenModel(item, child, index.toString())
                }
            } else {
                child = DefaultMutableTreeNode(Descriptor(icon = AllIcons.Json.Object, label = label))
                parent.add(child)
                node.fields().forEach {
                    buildChildrenModel(it.value, child, it.key)
                }
            }
        }
    }
}

class Descriptor(val value:Any? = null, val icon: Icon? = null, val label:String? = null)