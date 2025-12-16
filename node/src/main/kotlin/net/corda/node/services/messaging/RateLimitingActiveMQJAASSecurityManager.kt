package net.corda.node.services.messaging

import com.github.benmanes.caffeine.cache.Caffeine
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.security.auth.Subject
import javax.security.auth.login.FailedLoginException
import kotlin.math.pow

class RateLimitingActiveMQJAASSecurityManager(
        configurationName: String,
        configuration: SecurityConfiguration
) : ActiveMQJAASSecurityManager(configurationName, configuration) {

    private val ipLimiter = IpRateLimiter()

    override fun authenticate(user: String?, password: String?, remotingConnection: RemotingConnection?, securityDomain: String?): Subject? {
        val ip = extractIp(remotingConnection)

        // 1. Block if IP suspended
        ipLimiter.checkAllowed(ip)
        return try {
            val subject = super.authenticate(user, password, remotingConnection, securityDomain)
            // 2. Success - clear IP state
            ipLimiter.recordSuccess(ip)
            subject
        } catch (e: FailedLoginException) {
            // 3. Failure - record IP failure
            ipLimiter.recordFailure(ip)
            throw e
        }
    }

    /**
     * Extracts the remote IP address without the port.
     */
    private fun extractIp(remotingConnection: RemotingConnection?): String {
        val raw = remotingConnection?.remoteAddress ?: "unknown"
        return raw.substringAfter('/').substringBefore(':')
    }
}

internal class IpRateLimiter(
        private val maxFailuresBeforeBackoff: Int = 100,
        private val baseDelaySeconds: Long = 2,
        private val maxDelaySeconds: Long = 60
) {

    private data class Attempt(val count: Int, val nextAllowed: Instant)

    @Suppress("MagicNumber")
    private val attempts =
            Caffeine.newBuilder()
                    .expireAfterWrite(15, TimeUnit.MINUTES)
                    .maximumSize(10_000)
                    .build<String, Attempt>()

    fun checkAllowed(ip: String, now: Instant = Instant.now()) {
        val attempt = attempts.getIfPresent(ip) ?: return
        if (now.isBefore(attempt.nextAllowed)) {
            val remaining = attempt.nextAllowed.epochSecond - now.epochSecond
            throw FailedLoginException(
                    "Login temporarily suspended from IP $ip. Try again in $remaining seconds."
            )
        }
    }

    fun recordFailure(ip: String, now: Instant = Instant.now()) {
        val prev = attempts.getIfPresent(ip)
        val newCount = (prev?.count ?: 0) + 1

        val delay =
                if (newCount <= maxFailuresBeforeBackoff) 0
                else {
                    val exp = newCount - maxFailuresBeforeBackoff - 1
                    (baseDelaySeconds * 2.0.pow(exp)).toLong().coerceAtMost(maxDelaySeconds)
                }

        attempts.put(ip, Attempt(newCount, now.plusSeconds(delay)))
    }

    fun recordSuccess(ip: String) {
        attempts.invalidate(ip)
    }
}