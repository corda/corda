package net.corda.node.services.messaging

import net.corda.node.services.statemachine.SessionId
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.lang.IllegalArgumentException
import java.math.BigInteger

class SessionIdTest {

    @Test(timeout=300_000)
    fun `session identifier cannot be negative`() {
        assertThatThrownBy { SessionId(BigInteger.valueOf(-1)) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("Session identifier cannot be a negative number, but it was -1")
    }

    @Test(timeout=300_000)
    fun `session identifier needs to be a number that can be represented in maximum 128 bits`() {
        val largestSessionIdentifierValue = BigInteger.valueOf(2).pow(128).minus(BigInteger.ONE)
        val largestValidSessionId = SessionId(largestSessionIdentifierValue)

        assertThatThrownBy { SessionId(largestSessionIdentifierValue.plus(BigInteger.ONE)) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("The size of a session identifier cannot exceed 128 bits, but it was 340282366920938463463374607431768211456")
    }

}