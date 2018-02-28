package net.corda.behave.process

import net.corda.behave.node.Distribution
import org.assertj.core.api.Assertions.*
import org.junit.Test
import rx.observers.TestSubscriber

class CommandTests {

    @Test
    fun `successful command returns zero`() {
        val exitCode = Command(listOf("ls", "/")).run()
        assertThat(exitCode).isEqualTo(0)
    }

    @Test
    fun `successful launch rpc proxy`() {
        val cordaDistribution = Distribution.LATEST_MASTER.path
        val exitCode = Command(listOf("$cordaDistribution/startRPCproxy.sh", "$cordaDistribution")).run()
        assertThat(exitCode).isEqualTo(0)
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