package net.corda.node.services.messaging

import org.junit.Test
import java.time.Instant
import javax.security.auth.login.FailedLoginException
import kotlin.test.assertFailsWith

class IpRateLimiterTest {

    @Test
    fun `IP is suspended after too many failures`() {
        val limiter = IpRateLimiter(maxFailuresBeforeBackoff = 3)
        val ip = "127.0.0.1"
        val now = Instant.now()

        repeat(3) {
            limiter.recordFailure(ip, now)
        }

        limiter.recordFailure(ip, now)

        assertFailsWith<FailedLoginException> {
            limiter.checkAllowed(ip, now)
        }
    }

    @Test
    fun `IP suspension expires`() {
        val limiter = IpRateLimiter(maxFailuresBeforeBackoff = 1, baseDelaySeconds = 2)
        val ip = "127.0.0.1"
        val now = Instant.now()

        limiter.recordFailure(ip, now)
        limiter.recordFailure(ip, now)

        // still suspended
        assertFailsWith<FailedLoginException> {
            limiter.checkAllowed(ip, now.plusSeconds(1))
        }

        // suspension expired
        limiter.checkAllowed(ip, now.plusSeconds(3))
    }
}