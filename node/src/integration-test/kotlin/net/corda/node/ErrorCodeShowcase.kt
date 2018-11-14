package net.corda.node

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.messaging.FlowHandle
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.node.services.Permissions.Companion.all
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File

class ErrorCodeShowcase {
    private companion object {
        val ALICE = TestIdentity(CordaX500Name("ALICE", "London", "GB"))
    }

    private val user = User("mark", "dadada", setOf(all()))
    private val users = listOf(user)

    @Test
    fun `error code is shown for missing initiated flow`() {
        driver(DriverParameters(startNodesInProcess = false, notarySpecs = emptyList())) {

            val node = startNode(rpcUsers = users).getOrThrow()

            node.rpc.apply { startFlow(::NoAnswerFlow).waitForCompletion() }

            val logFile = node.logFile()
            val linesWithErrorCode = logFile.useLines { lines -> lines.filter { line -> line.contains("[errorCode=") }.toList() }
            assertThat(linesWithErrorCode).isNotEmpty
            linesWithErrorCode.forEach(::println)
        }
    }

    @InitiatingFlow
    @StartableByRPC
    class NoAnswerFlow : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            println("Is anyone there!? Hello?")
            val party = ALICE.party
            val answer = initiateFlow(party).receive<String>().unwrap { it }
            println("Got answer: $answer")
        }
    }

    private fun FlowHandle<*>.waitForCompletion() {
        try {
            returnValue.getOrThrow()
        } catch (e: Exception) {
            // This is expected to throw an exception, using getOrThrow() just to wait until done.
        }
    }

    private fun NodeHandle.logFile(): File = (baseDirectory / "logs").toFile().walk().filter { it.name.startsWith("node-") && it.extension == "log" }.single()
}