package net.corda.behave.process

import net.corda.behave.file.tmpDirectory
import net.corda.behave.node.Distribution
import net.corda.core.internal.delete
import net.corda.core.internal.div
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Files

class RPCProxyCommandTests {

    /**
     * Ensure you have configured the environment correctly by running the "prepare.sh" script
     * and then use a JVM option to ensure that STAGING_ROOT points to corresponding location used in the above script.
     * eg.
     *  -ea -DSTAGING_ROOT=/home/staging
     */

    @Test
    fun `successful launch rpc proxy`() {
        val cordaDistribution = Distribution.MASTER.path
        val portNo = 13000
        val command = Command(listOf("$cordaDistribution/startRPCproxy.sh", "$cordaDistribution", "$portNo"))
        val exitCode = command.run()
        assertThat(exitCode).isEqualTo(0)

        val pid = Files.lines(tmpDirectory / "rpcProxy-pid-$portNo").findFirst().get()
        println("Killing RPCProxyServer with pid: $pid")
        Command(listOf("kill", "-9", "$pid")).run()
        (tmpDirectory / "rpcProxy-pid-$portNo").delete()
    }
}