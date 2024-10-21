package net.corda.node.services.rpc

import co.paralleluniverse.fibers.Suspendable
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.internal.readFully
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.minutes
import net.corda.core.utilities.seconds
import net.corda.node.internal.NodeStartup
import net.corda.node.services.Permissions
import net.corda.node.services.statemachine.Checkpoint
import net.corda.node.services.statemachine.CountUpDownLatch
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.checkpoint.CheckpointRpcHelper.checkpointsRpc
import net.corda.testing.node.User
import net.corda.testing.node.internal.enclosedCordapp
import org.junit.Test
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.zip.ZipInputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.test.assertEquals

class DumpCheckpointsTest {

    companion object {
        private val dumpCheckPointLatch = CountDownLatch(1)
        private val flowProceedLatch = CountUpDownLatch(1)
    }

    @Test(timeout = 300_000)
    fun `verify checkpoint dump via RPC`() {
        val user = User("mark", "dadada", setOf(Permissions.all()))
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true, cordappsForAllNodes = listOf(enclosedCordapp()))) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()

            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {

                // 1 for GetNumberOfCheckpointsFlow itself
                val checkPointCountFuture = it.proxy.startFlow(::GetNumberOfCheckpointsFlow).returnValue

                val logDirPath = nodeAHandle.baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME
                logDirPath.createDirectories()
                dumpCheckPointLatch.await()
                nodeAHandle.checkpointsRpc.use { checkpointRPCOps -> checkpointRPCOps.dumpCheckpoints() }

                flowProceedLatch.countDown()
                assertEquals(1, checkPointCountFuture.get())
                checkDumpFile(logDirPath, GetNumberOfCheckpointsFlow::class.java, Checkpoint.FlowStatus.RUNNABLE)
            }
        }
    }

    @Test(timeout = 300_000)
    fun `paused flows included in checkpoint dump output`() {
        val user = User("mark", "dadada", setOf(Permissions.all()))
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true, cordappsForAllNodes = listOf(enclosedCordapp()))) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()

            CordaRPCClient(nodeAHandle.rpcAddress).start(user.username, user.password).use {

                it.proxy.startFlow(::EasyFlow)

                // Hack to get the flow to show as paused
                it.proxy.startFlow(::SetAllFlowsToPausedFlow).returnValue.getOrThrow(10.seconds)

                val logDirPath = nodeAHandle.baseDirectory / NodeStartup.LOGS_DIRECTORY_NAME
                logDirPath.createDirectories()
                nodeAHandle.checkpointsRpc.use { checkpointRPCOps -> checkpointRPCOps.dumpCheckpoints() }

                checkDumpFile(logDirPath, EasyFlow::class.java, Checkpoint.FlowStatus.PAUSED)
            }
        }
    }

    private fun checkDumpFile(dir: Path, containsClass: Class<out FlowLogic<*>>, flowStatus: Checkpoint.FlowStatus) {
        // The directory supposed to contain a single ZIP file
        val file = dir.listDirectoryEntries().single { it.isRegularFile() }

        ZipInputStream(file.inputStream()).use { zip ->
            val entry = zip.nextEntry!!
            assertThat(entry.name, containsSubstring("json"))
            val content = String(zip.readFully())
            assertThat(content, containsSubstring(containsClass.name))
            assertThat(content, containsSubstring(flowStatus.name))
        }
    }

    @StartableByRPC
    class GetNumberOfCheckpointsFlow : FlowLogic<Int>() {
        @Suspendable
        override fun call(): Int {
            var count = 0
            serviceHub.jdbcSession().prepareStatement("select * from node_checkpoints").use { ps ->
                ps.executeQuery().use { rs ->
                    while (rs.next()) {
                        count++
                    }
                }
            }
            syncUp()
            return count
        }

        @Suspendable
        private fun syncUp() {
            dumpCheckPointLatch.countDown()
            flowProceedLatch.await()
        }
    }

    @StartableByRPC
    class EasyFlow : FlowLogic<Int>() {
        @Suspendable
        override fun call(): Int {
            sleep(2.minutes)
            return 1
        }
    }

    @StartableByRPC
    class SetAllFlowsToPausedFlow : FlowLogic<Int>() {
        @Suspendable
        override fun call(): Int {
            return serviceHub
                .jdbcSession()
                .prepareStatement("UPDATE node_checkpoints SET status = '${Checkpoint.FlowStatus.PAUSED.ordinal}'")
                .use { ps -> ps.executeUpdate() }
        }
    }
}