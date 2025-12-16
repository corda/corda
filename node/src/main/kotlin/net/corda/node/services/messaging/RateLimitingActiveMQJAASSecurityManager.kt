package net.corda.node.services.messaging

import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.core.utilities.loggerFor
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.security.auth.Subject
import javax.security.auth.login.FailedLoginException
import kotlin.math.pow

private data class Attempt(val count: Int, val nextAllowed: Instant)

class RateLimitingActiveMQJAASSecurityManager(
        configurationName: String,
        configuration: SecurityConfiguration
) : ActiveMQJAASSecurityManager(configurationName, configuration) {

    companion object {
        private val log = loggerFor<ActiveMQJAASSecurityManager>()
    }

    private val userFreeAttempts = 3
    private val ipFreeAttempts = 100
    private val baseDelaySeconds = 2L
    private val maxDelaySeconds = 60L

    private val ipAttempts =
            Caffeine.newBuilder()
                    .expireAfterWrite(15, TimeUnit.MINUTES)
                    .maximumSize(10_000)
                    .build<String, Attempt>()

    private val userAttempts =
            Caffeine.newBuilder()
                    .expireAfterWrite(15, TimeUnit.MINUTES)
                    .maximumSize(10_000)
                    .build<String, Attempt>()

    override fun authenticate(user: String?, password: String?, remotingConnection: RemotingConnection?, securityDomain: String?): Subject? {
        val now = Instant.now()
        val ip = extractIp(remotingConnection) ?: "unknown"
        val userKey = hashUserWithIp(user, ip)

        // 1. Block if IP suspended
        ipAttempts.getIfPresent(ip)?.let { attempt ->
            if (now.isBefore(attempt.nextAllowed)) {
                val remaining = attempt.nextAllowed.epochSecond - now.epochSecond
                throw FailedLoginException("Login temporarily suspended from IP $ip. Try again in $remaining seconds.")
            }
        }

        // Block if user+IP suspended
        userAttempts.getIfPresent(userKey)?.let { attempt ->
            if (now.isAfter(attempt.nextAllowed)) {
                val remaining = attempt.nextAllowed.epochSecond - now.epochSecond
                throw FailedLoginException("Login temporarily suspended for user '$user'. Try again in $remaining seconds.")
            }
        }

        return try {
            val subject = super.authenticate(user, password, remotingConnection, securityDomain)
            userAttempts.invalidate(userKey)
            subject
        } catch (e: Exception) {
            recordFailure(now, ip, userKey)
            throw e
        }
    }

    private fun recordFailure(now: Instant, ip: String, userKey: String) {
        recordIpFailure(now, ip)
        recordUserFailure(now, userKey)
    }

    private fun recordIpFailure(now: Instant, ip: String) {
        val prev = ipAttempts.getIfPresent(ip)
        val count = (prev?.count ?: 0) + 1

        if (count <= ipFreeAttempts) {
            ipAttempts.put(ip, Attempt(count, now))
            return
        }

        val delay = computeDelay(count - ipFreeAttempts)
        ipAttempts.put(ip, Attempt(count, now.plusSeconds(delay)))
    }

    private fun recordUserFailure(now: Instant, userKey: String) {
        val prev = userAttempts.getIfPresent(userKey)
        val count = (prev?.count ?: 0) + 1

        if (count <= userFreeAttempts) {
            userAttempts.put(userKey, Attempt(count, now))
            return
        }

        val delay = computeDelay(count - userFreeAttempts)
        userAttempts.put(userKey, Attempt(count, now.plusSeconds(delay)))
    }

    private fun computeDelay(backoffStep: Int): Long =
            (baseDelaySeconds * 2.0.pow(backoffStep - 1))
                    .toLong()
                    .coerceAtMost(maxDelaySeconds)

    private fun extractIp(connection: RemotingConnection?): String? {
        val address = connection?.remoteAddress ?: return null
        return address.substringAfter("/").substringBefore(":")
    }

    private fun hashUserWithIp(user: String?, ip: String): String =
            "${user ?: "unknown"}@$ip".hashCode().toString()
}