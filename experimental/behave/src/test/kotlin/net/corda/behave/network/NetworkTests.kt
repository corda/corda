package net.corda.behave.network

import net.corda.behave.database.DatabaseType
import net.corda.behave.node.Distribution
import net.corda.behave.node.configuration.NotaryType
import net.corda.core.utilities.hours
import net.corda.core.utilities.seconds
import org.junit.Ignore
import org.junit.Test
import java.time.Duration

class NetworkTests {

    @Ignore
    @Test
    fun `OS Corda network of single node with RPC proxy can be spun up`() {
        val distribution = Distribution.MASTER
        val network = Network
                .new()
                .addNode(name = "Foo", distribution = distribution, notaryType = NotaryType.NON_VALIDATING, withRPCProxy = true)
                .generate()
        network.use {
            it.waitUntilRunning(300.seconds)
            it.keepAlive(300.seconds)
            it.signal()
        }
    }

    @Ignore
    @Test
    fun `Corda Enterprise network of single node with RPC proxy can be spun up`() {
        val network = Network
                .new(Distribution.Type.CORDA_ENTERPRISE)
                .addNode(name = "Notary", distribution = Distribution.R3_MASTER, notaryType = NotaryType.NON_VALIDATING, withRPCProxy = true)
                .generate()
        network.use {
            it.waitUntilRunning(1.hours)
            it.keepAlive(1.hours)
            it.signal()
        }
    }

    @Ignore
    @Test
    fun `Mixed OS Corda network of two nodes (with an RPC proxy each) and a non-validating notary can be spun up`() {
        // Note: this test exercises the NetworkBootstrapper to setup a local network
        val distribution = Distribution.MASTER
        val network = Network
                .new()
                .addNode(name = "EntityA", distribution = Distribution.MASTER, withRPCProxy = true)
                .addNode(name = "EntityB", distribution = Distribution.R3_MASTER, withRPCProxy = true)
                .addNode(name = "Notary", distribution = distribution, notaryType = NotaryType.NON_VALIDATING)
                .generate()
        network.use {
            it.waitUntilRunning(Duration.ofDays(1))
            it.keepAlive(Duration.ofDays(1))
        }
    }

    @Ignore
    @Test
    fun `Mixed Corda Enterprise network of two nodes (with an RPC proxy each) and a non-validating notary can be spun up`() {
        // Note: this test exercises the Doorman / Notary / NMS setup sequence
        val distribution = Distribution.R3_MASTER
        val network = Network
                .new()
                .addNode(name = "EntityA", distribution = Distribution.R3_MASTER, withRPCProxy = true)
                .addNode(name = "EntityB", distribution = Distribution.MASTER, withRPCProxy = true)
                .addNode(name = "Notary", distribution = distribution, notaryType = NotaryType.NON_VALIDATING)
                .generate()
        network.use {
            it.waitUntilRunning(Duration.ofDays(1))
            it.keepAlive(Duration.ofDays(1))
        }
    }

    @Ignore
    @Test
    fun `network of two nodes can be spun up`() {
        val network = Network
                .new()
                .addNode("Foo")
                .addNode("Bar")
                .generate()
        network.use {
            it.waitUntilRunning(30.seconds)
            it.signal()
            it.keepAlive(30.seconds)
        }
    }

    @Ignore
    @Test
    fun `network of three nodes and mixed databases can be spun up`() {
        val network = Network
                .new()
                .addNode("Foo")
                .addNode("Bar", databaseType = DatabaseType.POSTGRES)
                .addNode("Baz", notaryType = NotaryType.NON_VALIDATING)
                .generate()
        network.use {
            it.waitUntilRunning(30.seconds)
            it.signal()
            it.keepAlive(30.seconds)
        }
    }

    @Ignore
    @Test
    fun `Corda Enterprise network of single node using Oracle 11g can be spun up`() {
        val network = Network
                .new(Distribution.Type.CORDA_ENTERPRISE)
                .addNode(name = "Notary", distribution = Distribution.R3_MASTER, notaryType = NotaryType.NON_VALIDATING, databaseType = DatabaseType.ORACLE_11G)
                .generate()
        network.use {
            it.waitUntilRunning(30.seconds)
            it.keepAlive(30.seconds)
            it.signal()
        }
    }

    @Ignore
    @Test
    fun `Corda Enterprise network of single node using Oracle 12c can be spun up`() {
        val network = Network
                .new(Distribution.Type.CORDA_ENTERPRISE)
                .addNode(name = "Notary", distribution = Distribution.R3_MASTER, notaryType = NotaryType.NON_VALIDATING, databaseType = DatabaseType.ORACLE_12C)
                .generate()
        network.use {
            it.waitUntilRunning(30.seconds)
            it.keepAlive(30.seconds)
            it.signal()
        }
    }
}