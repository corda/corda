package net.corda.behave.process

import net.corda.behave.node.Distribution
import org.apache.commons.io.FileUtils
import org.assertj.core.api.Assertions.*
import org.junit.Test
import rx.observers.TestSubscriber
import java.nio.file.Files
import java.nio.file.Paths

class CommandTests {

    @Test
    fun `successful command returns zero`() {
        val exitCode = Command(listOf("ls", "/")).run()
        assertThat(exitCode).isEqualTo(0)
    }

    @Test
    fun `successful launch rpc proxy`() {
        val cordaDistribution = Distribution.LATEST_MASTER.path
        val command = Command(listOf("./startRPCproxy.sh", "$cordaDistribution"))
        val exitCode = command.run()
        assertThat(exitCode).isEqualTo(0)

        val pid = Files.lines(Paths.get("/tmp/rpcProxy-pid")).findFirst().get()
        println("Killing RPCProxyServer with pid: $pid")
        Command(listOf("kill", "-9", "$pid")).run()
        FileUtils.deleteQuietly(Paths.get("/tmp/rpcProxy-pid").toFile())
    }

    @Test
    fun `failed command returns non-zero`() {
        val exitCode = Command(listOf("ls", "some-weird-path-that-does-not-exist")).run()
        assertThat(exitCode).isNotEqualTo(0)
    }

    @Test
    fun `output stream for command can be observed`() {
        val subscriber = TestSubscriber<String>()
        val exitCode = Command(listOf("ls", "/")).use { _, output ->
            output.subscribe(subscriber)
            subscriber.awaitTerminalEvent()
            subscriber.assertCompleted()
            subscriber.assertNoErrors()
            assertThat(subscriber.onNextEvents).contains("bin", "etc", "var")
        }
        assertThat(exitCode).isEqualTo(0)
    }

}