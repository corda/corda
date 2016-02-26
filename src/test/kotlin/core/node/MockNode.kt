/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.node

import com.google.common.jimfs.Jimfs
import com.google.common.util.concurrent.MoreExecutors
import core.messaging.InMemoryMessagingNetwork
import core.messaging.LegallyIdentifiableNode
import core.messaging.MessagingService
import core.utilities.loggerFor
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ExecutorService

/**
 * A mock node brings up a suite of in-memory services in a fast manner suitable for unit testing.
 * Components that do IO are either swapped out for mocks, or pointed to a [Jimfs] in memory filesystem.
 *
 * Mock network nodes require manual pumping by default: they will not run asynchronous. This means that
 * for message exchanges to take place (and associated handlers to run), you must call the [runNetwork]
 * method.
 */
class MockNetwork {
    private var counter = 0
    val filesystem = Jimfs.newFileSystem()
    val messagingNetwork = InMemoryMessagingNetwork()

    private val _nodes = ArrayList<MockNode>()
    /** A read only view of the current set of executing nodes. */
    val nodes: List<MockNode> = _nodes

    init {
        Files.createDirectory(filesystem.getPath("/nodes"))
    }

    open class MockNode(dir: Path, config: NodeConfiguration, val network: InMemoryMessagingNetwork,
                        withTimestamper: LegallyIdentifiableNode?) : AbstractNode(dir, config, withTimestamper) {
        override val log: Logger = loggerFor<MockNode>()
        override val serverThread: ExecutorService = MoreExecutors.newDirectExecutorService()

        // We only need to override the messaging service here, as currently everything that hits disk does so
        // through the java.nio API which we are already mocking via Jimfs.

        override fun makeMessagingService(): MessagingService {
            return network.createNode(true).second.start().get()
        }

        override fun start(): MockNode {
            super.start()
            return this
        }
    }

    fun createNode(withTimestamper: LegallyIdentifiableNode?,
                   factory: (Path, NodeConfiguration, network: InMemoryMessagingNetwork, LegallyIdentifiableNode?) -> MockNode = { p, n, n2, l -> MockNode(p, n, n2, l) }): MockNode {
        val path = filesystem.getPath("/nodes/$counter")
        Files.createDirectory(path)
        val config = object : NodeConfiguration {
            override val myLegalName: String = "Mock Company $counter"
        }
        val node = factory(path, config, messagingNetwork, withTimestamper).start()
        _nodes.add(node)
        counter++
        return node
    }

    /**
     * Asks every node in order to process any queued up inbound messages. This may in turn result in nodes
     * sending more messages to each other, thus, a typical usage is to call runNetwork with the [rounds]
     * parameter set to -1 (the default) which simply runs as many rounds as necessary to result in network
     * stability (no nodes sent any messages in the last round).
     */
    fun runNetwork(rounds: Int = -1) {
        fun pumpAll() = messagingNetwork.endpoints.map { it.pump(false) }
        if (rounds == -1)
            while (pumpAll().any { it }) {}
        else
            repeat(rounds) { pumpAll() }
    }

    /**
     * Sets up a two node network in which the first node runs a timestamping service and the other doesn't.
     */
    fun createTwoNodes(): Pair<MockNode, MockNode> {
        require(nodes.isEmpty())
        return Pair(createNode(null), createNode(nodes[0].legallyIdentifableAddress))
    }
}