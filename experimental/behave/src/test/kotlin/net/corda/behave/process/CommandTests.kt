/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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
        val exitCode = Command(listOf("ls", "/")).use(subscriber) { _, output ->
            subscriber.awaitTerminalEvent()
            subscriber.assertCompleted()
            subscriber.assertNoErrors()
            assertThat(subscriber.onNextEvents).contains("bin", "etc", "var")
        }
        assertThat(exitCode).isEqualTo(0)
    }
}