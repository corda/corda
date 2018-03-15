package net.corda.behave.network

import net.corda.behave.database.DatabaseType
import net.corda.behave.node.Distribution
import net.corda.behave.node.configuration.NotaryType
import net.corda.behave.seconds
import net.corda.core.utilities.hours
import org.junit.Ignore
import org.junit.Test
import java.time.Duration

class NetworkTests {

    @Ignore
    @Test
    fun `OS Corda network of single node with RPC proxy can be spun up`() {
        val distribution = Distribution.LATEST_MASTER
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
    fun `R3 Corda network of single node with RPC proxy can be spun up`() {
        val network = Network
                .new()
                .addNode(name = "Foo", distribution = Distribution.R3_MASTER, notaryType = NotaryType.NON_VALIDATING, withRPCProxy = true)
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
        val distribution = Distribution.LATEST_MASTER
        val network = Network
                .new()
                .addNode(name = "EntityA", distribution = Distribution.LATEST_MASTER, withRPCProxy = true)
                .addNode(name = "EntityB", distribution = Distribution.LATEST_R3_MASTER, withRPCProxy = true)
                .addNode(name = "Notary", distribution = distribution, notaryType = NotaryType.NON_VALIDATING)
                .generate()
        network.use {
            it.waitUntilRunning(Duration.ofDays(1))
            it.keepAlive(Duration.ofDays(1))
        }
    }

    @Ignore
    @Test
    fun `Mixed R3 Corda network of two nodes (with an RPC proxy each) and a non-validating notary can be spun up`() {
        // Note: this test exercises the Doorman / Notary / NMS setup sequence
        val distribution = Distribution.LATEST_R3_MASTER
        val network = Network
                .new()
                .addNode(name = "EntityA", distribution = Distribution.LATEST_R3_MASTER, withRPCProxy = true)
                .addNode(name = "EntityB", distribution = Distribution.LATEST_MASTER, withRPCProxy = true)
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
                .addNode("Bar", databaseType = DatabaseType.SQL_SERVER)
                .addNode("Baz", notaryType = NotaryType.NON_VALIDATING)
                .generate()
        network.use {
            it.waitUntilRunning(30.seconds)
            it.signal()
            it.keepAlive(30.seconds)
        }
    }

}