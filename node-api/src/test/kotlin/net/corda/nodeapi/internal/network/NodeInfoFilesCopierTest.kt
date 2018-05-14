/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.network

import net.corda.cordform.CordformNode
import net.corda.core.internal.div
import net.corda.core.internal.list
import net.corda.core.internal.write
import net.corda.nodeapi.eventually
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import rx.schedulers.TestScheduler
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

class NodeInfoFilesCopierTest {
    companion object {
        private const val ORGANIZATION = "Organization"
        private const val NODE_1_PATH = "node1"
        private const val NODE_2_PATH = "node2"

        private val content = "blah".toByteArray(Charsets.UTF_8)
        private const val GOOD_NODE_INFO_NAME = "${NodeInfoFilesCopier.NODE_INFO_FILE_NAME_PREFIX}test"
        private const val GOOD_NODE_INFO_NAME_2 = "${NodeInfoFilesCopier.NODE_INFO_FILE_NAME_PREFIX}anotherNode"
        private const val BAD_NODE_INFO_NAME = "something"
    }

    @Rule
    @JvmField
    val folder = TemporaryFolder()

    private val rootPath get() = folder.root.toPath()
    private val scheduler = TestScheduler()

    private fun nodeDir(nodeBaseDir : String) = rootPath.resolve(nodeBaseDir).resolve(ORGANIZATION.toLowerCase())

    private val node1RootPath by lazy { nodeDir(NODE_1_PATH) }
    private val node2RootPath by lazy { nodeDir(NODE_2_PATH) }
    private val node1AdditionalNodeInfoPath by lazy { node1RootPath.resolve(CordformNode.NODE_INFO_DIRECTORY) }
    private val node2AdditionalNodeInfoPath by lazy { node2RootPath.resolve(CordformNode.NODE_INFO_DIRECTORY) }

    private lateinit var nodeInfoFilesCopier: NodeInfoFilesCopier

    @Before
    fun setUp() {
        nodeInfoFilesCopier = NodeInfoFilesCopier(scheduler)
    }

    @Test
    fun `files created before a node is started are copied to that node`() {
        // Configure the first node.
        nodeInfoFilesCopier.addConfig(node1RootPath)
        // Ensure directories are created.
        advanceTime()

        // Create 2 files, a nodeInfo and another file in node1 folder.
        (node1RootPath / GOOD_NODE_INFO_NAME).write(content)
        (node1RootPath / BAD_NODE_INFO_NAME).write(content)

        // Configure the second node.
        nodeInfoFilesCopier.addConfig(node2RootPath)
        advanceTime()

        eventually<AssertionError, Unit>(Duration.ofMinutes(1)) {
            // Check only one file is copied.
            checkDirectoryContainsSingleFile(node2AdditionalNodeInfoPath, GOOD_NODE_INFO_NAME)
        }
    }

    @Test
    fun `polling of running nodes`() {
        // Configure 2 nodes.
        nodeInfoFilesCopier.addConfig(node1RootPath)
        nodeInfoFilesCopier.addConfig(node2RootPath)
        advanceTime()

        // Create 2 files, one of which to be copied, in a node root path.
        (node2RootPath / GOOD_NODE_INFO_NAME).write(content)
        (node2RootPath / BAD_NODE_INFO_NAME).write(content)
        advanceTime()

        eventually<AssertionError, Unit>(Duration.ofMinutes(1)) {
            // Check only one file is copied to the other node.
            checkDirectoryContainsSingleFile(node1AdditionalNodeInfoPath, GOOD_NODE_INFO_NAME)
        }
    }

    @Test
    fun `remove nodes`() {
        // Configure 2 nodes.
        nodeInfoFilesCopier.addConfig(node1RootPath)
        nodeInfoFilesCopier.addConfig(node2RootPath)
        advanceTime()

        // Create a file, in node 2 root path.
        (node2RootPath / GOOD_NODE_INFO_NAME).write(content)
        advanceTime()

        // Remove node 2
        nodeInfoFilesCopier.removeConfig(node2RootPath)

        // Create another file in node 2 directory.
        (node2RootPath / GOOD_NODE_INFO_NAME).write(content)
        advanceTime()

        eventually<AssertionError, Unit>(Duration.ofMinutes(1)) {
            // Check only one file is copied to the other node.
            checkDirectoryContainsSingleFile(node1AdditionalNodeInfoPath, GOOD_NODE_INFO_NAME)
        }
    }

    @Test
    fun clear() {
        // Configure 2 nodes.
        nodeInfoFilesCopier.addConfig(node1RootPath)
        nodeInfoFilesCopier.addConfig(node2RootPath)
        advanceTime()

        nodeInfoFilesCopier.reset()

        advanceTime()
        (node2RootPath / GOOD_NODE_INFO_NAME_2).write(content)

        // Give some time to the filesystem to report the change.
        Thread.sleep(100)
        assertThat(node1AdditionalNodeInfoPath.list()).isEmpty()
    }

    private fun advanceTime() {
        scheduler.advanceTimeBy(1, TimeUnit.HOURS)
    }

    private fun checkDirectoryContainsSingleFile(path: Path, filename: String) {
        val files = path.list()
        assertThat(files).hasSize(1)
        assertThat(files[0].fileName.toString()).isEqualTo(filename)
    }
}