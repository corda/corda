package net.corda.behave.process

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import rx.observers.TestSubscriber

class CommandTests {
    @Test
    fun `successful command returns zero`() {
        val exitCode = Command(listOf("ls", "/")).run()
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
        val exitCode = Command(listOf("ls", "/")).run(subscriber) { _, _ ->
            subscriber.awaitTerminalEvent()
            subscriber.assertCompleted()
            subscriber.assertNoErrors()
            assertThat(subscriber.onNextEvents).contains("bin", "etc", "var")
        }
        assertThat(exitCode).isEqualTo(0)
    }
}