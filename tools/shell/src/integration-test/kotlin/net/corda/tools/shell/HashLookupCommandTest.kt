package net.corda.tools.shell

import co.paralleluniverse.fibers.Suspendable
import com.google.common.io.Files
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.bouncycastle.util.io.Streams
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertTrue

class HashLookupCommandTest {
    @Ignore
    @Test
    fun `hash lookup command returns correct response`() {
        val user = User("u", "p", setOf(Permissions.all()))

        driver(DriverParameters(notarySpecs = emptyList(), extraCordappPackagesToScan = listOf("net.corda.testing.contracts"))) {
            val nodeFuture = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user), startInSameProcess = true)
            val node = nodeFuture.getOrThrow()
            val txId = issueTransaction(node)

            val session = connectToShell(user, node)

            testCommand(session, command = "hashLookup ${txId.sha256()}", expected = "Found a matching transaction with Id: $txId")
            testCommand(session, command = "hashLookup ${SecureHash.randomSHA256()}", expected = "No matching transaction found")

            session.disconnect()
        }
    }

    private fun testCommand(session: Session, command: String, expected: String) {
        val channel = session.openChannel("exec") as ChannelExec
        channel.setCommand(command)
        channel.connect(5000)

        assertTrue(channel.isConnected)

        val response = String(Streams.readAll(channel.inputStream))
        val matchFound = response.lines().any { line ->
            line.contains(expected)
        }
        channel.disconnect()
        assertTrue(matchFound)
    }

    private fun connectToShell(user: User, node: NodeHandle): Session {
        val conf = ShellConfiguration(commandsDirectory = Files.createTempDir().toPath(),
                user = user.username, password = user.password,
                hostAndPort = node.rpcAddress,
                sshdPort = 2224)

        InteractiveShell.startShell(conf)
        InteractiveShell.nodeInfo()

        val session = JSch().getSession("u", "localhost", 2224)
        session.setConfig("StrictHostKeyChecking", "no")
        session.setPassword("p")
        session.connect()

        assertTrue(session.isConnected)
        return session
    }

    private fun issueTransaction(node: NodeHandle): SecureHash {
        return node.rpc.startFlow(::DummyIssue).returnValue.get()
    }

    @StartableByRPC
    internal class DummyIssue : FlowLogic<SecureHash>() {
        @Suspendable
        override fun call(): SecureHash {
            val me = serviceHub.myInfo.legalIdentities.first().ref(0)
            val fakeNotary = me.party
            val builder = DummyContract.generateInitial(1, fakeNotary as Party, me)
            val stx = serviceHub.signInitialTransaction(builder)
            serviceHub.recordTransactions(stx)
            return stx.id
        }
    }
}