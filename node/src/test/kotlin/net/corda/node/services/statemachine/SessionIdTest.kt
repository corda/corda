package net.corda.node.services.statemachine

import org.assertj.core.api.Assertions.*
import org.junit.Test
import java.lang.IllegalArgumentException
import java.math.BigInteger

class SessionIdTest {

    companion object {
        private val LARGEST_SESSION_ID_VALUE = BigInteger.valueOf(2).pow(128).minus(BigInteger.ONE)
    }

    @Test(timeout=300_000)
    fun `session id must be positive and representable in 128 bits`() {
        assertThatThrownBy { SessionId(BigInteger.ZERO.minus(BigInteger.ONE)) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("Session identifier cannot be a negative number, but it was -1")

        assertThatThrownBy { SessionId(LARGEST_SESSION_ID_VALUE.plus(BigInteger.ONE)) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("The size of a session identifier cannot exceed 128 bits, but it was")

        val correctSessionId = SessionId(LARGEST_SESSION_ID_VALUE)
    }

    @Test(timeout=300_000)
    fun `initiated session id is calculated properly`() {
        val sessionId = SessionId(BigInteger.ONE)
        val initiatedSessionId = sessionId.calculateInitiatedSessionId()
        assertThat(initiatedSessionId.value.toLong()).isEqualTo(2)

    }

    @Test(timeout=300_000)
    fun `calculation of initiated session id wraps around`() {
        val sessionId = SessionId(LARGEST_SESSION_ID_VALUE)
        val initiatedSessionId = sessionId.calculateInitiatedSessionId()
        assertThat(initiatedSessionId.value.toLong()).isEqualTo(0)
    }

}