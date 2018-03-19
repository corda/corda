package net.corda.behave.process

import net.corda.behave.node.Distribution
import org.apache.commons.io.FileUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class RPCProxyCommandTests {

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
}