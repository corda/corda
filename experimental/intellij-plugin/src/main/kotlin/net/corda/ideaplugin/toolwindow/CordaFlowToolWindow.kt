package net.corda.ideaplugin.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.*
import javax.swing.tree.*

/**
 * GUI class for the Corda Flow tool
 */
class CordaFlowToolWindow : ToolWindowFactory {
    // Left-hand side tree view
    private val flowTree = Tree()
    // Right-hand side tree view
    private val snapshotTree = Tree()
    // Main panel
    private val panel = JPanel(GridBagLayout())
    private val textField = JTextField()
    private val browseButton = JButton()
    private val refreshButton = JButton()

    private val snapshotDataManager = FlowSnapshotTreeDataManager(snapshotTree)
    private val flowDataManager = FlowTreeDataManager(flowTree, snapshotDataManager)

    init {
        setUpSnapshotTree()
        setUpFlowTree()
        setUpBrowseButton()
        setUpRefreshButton()
        setUpTextField()

        // Laying-out left-hand side panel
        val flowPanel = JPanel(BorderLayout(3, 3))
        flowPanel.add(BorderLayout.NORTH, JLabel("Flows"))
        val flowScrollPane = JBScrollPane(flowTree)
        flowScrollPane.border = BorderFactory.createEmptyBorder()
        flowPanel.add(BorderLayout.CENTER, flowScrollPane)

        // Laying-out right-hand side panel
        val snapshotPanel = JPanel(BorderLayout(3, 3))
        snapshotPanel.add(BorderLayout.NORTH, JLabel("Snapshots"))
        val snapshotScrollPane = JBScrollPane(snapshotTree)
        snapshotScrollPane.border = BorderFactory.createEmptyBorder()
        snapshotPanel.add(BorderLayout.CENTER, snapshotScrollPane)

        // Horizontal divider
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, flowPanel, snapshotPanel)
        splitPane.dividerSize = 2
        splitPane.resizeWeight = 0.5
        splitPane.border = BorderFactory.createEmptyBorder()

        // Container for the text field and buttons
        val topPanel = JPanel()
        topPanel.layout = BoxLayout(topPanel, BoxLayout.LINE_AXIS)
        topPanel.add(textField)
        topPanel.add(browseButton)
        topPanel.add(refreshButton)

        GridBagConstraints().apply {
            gridwidth = 1
            weightx = 0.05
            weighty = 0.05
            fill = GridBagConstraints.HORIZONTAL // Fill cell in both direction
            insets = Insets(3, 3, 3, 3)
            gridy = 0
            panel.add(topPanel, this)
        }
        GridBagConstraints().apply {
            gridwidth = 1
            weightx = 1.0 // Cell takes up all extra horizontal space
            weighty = 1.0 // Cell takes up all extra vertical space
            fill = GridBagConstraints.BOTH // Fill cell in both direction
            insets = Insets(3, 3, 3, 3)
            gridy = 1
            panel.add(splitPane, this)
        }
    }

    // Create the tool window content.
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory: ContentFactory = ContentFactory.SERVICE.getInstance()
        val content: Content = contentFactory.createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    private fun setUpSnapshotTree() {
        snapshotTree.isRootVisible = false
        snapshotTree.cellRenderer = SnapshotTreeRenderer()
    }

    private fun setUpFlowTree() {
        flowTree.isRootVisible = false
        flowTree.selectionModel = LeafOnlyTreeSelectionModel()
        flowTree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        flowTree.cellRenderer = FlowTreeRenderer()
        flowTree.addTreeSelectionListener {
            val node = flowTree.lastSelectedPathComponent
            if (node is DefaultMutableTreeNode) {
                val dir = node.userObject as File
                if (dir.exists()) {
                    snapshotDataManager.loadSnapshots(dir.listFiles().filter {
                        !it.isDirectory && it.name.startsWith(FlowSnapshotTreeDataManager.SNAPSHOT_FILE_PREFIX)
                    })
                }
            } else {
                snapshotDataManager.clear()
            }
        }
    }

    private fun setUpBrowseButton() {
        browseButton.icon = AllIcons.General.Ellipsis
        browseButton.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                val chooser = JFileChooser()
                chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                val chooserValue = chooser.showOpenDialog(null)
                if (chooserValue == JFileChooser.APPROVE_OPTION) {
                    var selectedFile = chooser.selectedFile
                    if (selectedFile.name != FlowTreeDataManager.FLOWS_DIRECTORY) {
                        val subDirectory = File(selectedFile, FlowTreeDataManager.FLOWS_DIRECTORY)
                        if (subDirectory.exists()) {
                            selectedFile = subDirectory
                        } else {
                            textField.text = "Could not find the ${FlowTreeDataManager.FLOWS_DIRECTORY} directory"
                            clear()
                            return
                        }
                    }
                    textField.text = selectedFile.canonicalPath
                    flowDataManager.loadFlows(selectedFile)
                }
            }
        })
    }

    private fun setUpRefreshButton() {
        refreshButton.icon = AllIcons.Actions.Refresh
        refreshButton.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                clear()
                flowDataManager.loadFlows()
            }
        })
    }

    private fun clear() {
        flowTree.clearSelection()
        flowDataManager.clear()
        snapshotTree.clearSelection()
        snapshotDataManager.clear()
    }

    private fun setUpTextField() {
        textField.isEditable = false
        textField.isFocusable = false
        textField.background = Color(0, 0, 0, 0)
        textField.text = "Choose flow data location (i.e. node base directory)"
    }

    private class FlowTreeRenderer : DefaultTreeCellRenderer() {
        override fun getTreeCellRendererComponent(tree: JTree?, value: Any?, sel: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean): Component {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
            val node = value as DefaultMutableTreeNode
            if (node.isLeaf) {
                icon = AllIcons.Debugger.Frame
            }
            val userObject = node.userObject
            if (userObject is File) {
                text = userObject.name
            }
            return this
        }
    }

    private class SnapshotTreeRenderer : DefaultTreeCellRenderer() {
        override fun getTreeCellRendererComponent(tree: JTree?, value: Any?, sel: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean): Component {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
            val descriptor = (value as DefaultMutableTreeNode).userObject as Descriptor
            icon = descriptor.icon
            if (!descriptor.label.isNullOrEmpty()) {
                text = descriptor.label + if (leaf) ": ${descriptor.value?.toString()}" else ""
            }
            return this
        }
    }

    private class LeafOnlyTreeSelectionModel : DefaultTreeSelectionModel() {
        override fun addSelectionPaths(paths: Array<out TreePath>?) {
            super.addSelectionPaths(filterPaths(paths))
        }

        override fun setSelectionPaths(paths: Array<out TreePath>?) {
            super.setSelectionPaths(filterPaths(paths))
        }

        private fun filterPaths(paths: Array<out TreePath>?): Array<TreePath>? {
            return paths?.filter { (it.lastPathComponent as DefaultMutableTreeNode).isLeaf }?.toTypedArray()
        }
    }
}