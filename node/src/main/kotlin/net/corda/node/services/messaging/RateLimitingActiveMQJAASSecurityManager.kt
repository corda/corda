package net.corda.node.services.messaging

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.security.auth.Subject
import javax.security.auth.login.FailedLoginException
import kotlin.math.pow

class RateLimitingActiveMQJAASSecurityManager(
        configurationName: String,
        configuration: SecurityConfiguration,
        rateLimitConfig: net.corda.node.services.config.SecurityConfiguration.AuthService.Options.RateLimit?
) : ActiveMQJAASSecurityManager(configurationName, configuration) {

    private data class Attempt(val count: Int, val nextAllowed: Instant)

    private val baseDelaySeconds = rateLimitConfig?.backoffBaseSeconds ?: 2L
    @Suppress("MagicNumber")
    private val maxDelaySeconds = rateLimitConfig?.backoffMaxSeconds ?: 60L
    @Suppress("MagicNumber")
    private val attemptExpireMinutes = rateLimitConfig?.attemptExpireMinutes ?: 15L

    private val userFreeAttempts = 3
    private val ipFreeAttempts = 10

    private val userAttempts =
            Caffeine.newBuilder()
                    .expireAfterWrite(attemptExpireMinutes, TimeUnit.MINUTES)
                    .maximumSize(10_000)
                    .build<String, Attempt>()

    private val ipAttempts =
            Caffeine.newBuilder()
                    .expireAfterWrite(15, TimeUnit.MINUTES)
                    .maximumSize(10_000)
                    .build<String, Attempt>()

    override fun authenticate(user: String?, password: String?, remotingConnection: RemotingConnection?, securityDomain: String?): Subject? {

        val now = Instant.now()
        val ip = extractIp(remotingConnection)
        val userKey = user?.let { hash("$it|$ip") }
        val ipKey = hash(ip)

        // 1. If user+IP suspended -> immediately reject
        if (userKey != null) {
            userAttempts.getIfPresent(userKey)?.let { userAttempt ->
                if (now.isBefore(userAttempt.nextAllowed)) {
                    val remaining = Duration.between(now, userAttempt.nextAllowed).seconds
                    throw FailedLoginException("Login temporarily suspended for user '$user'. Try again in $remaining seconds.")
                }
            }
        }

        // 2. Attempt authentication
        return try {
            val subject = super.authenticate(user, password, remotingConnection, securityDomain)

            // success - clear user cache only
            if (userKey != null) {
                userAttempts.invalidate(userKey)
            }
            subject
        } catch (fle: FailedLoginException) {

            // 3. Record IP failure
            recordFailure(ipAttempts, ipKey, ipFreeAttempts, now)

            // 4, If IP suspended -> reject
            ipAttempts.getIfPresent(ipKey)?.let { ipAttempt ->
                if (now.isBefore(ipAttempt.nextAllowed)) {
                    val remaining = Duration.between(now, ipAttempt.nextAllowed).seconds
                    throw FailedLoginException("Login temporarily suspended from IP $ip. Try again in $remaining seconds.")
                }
            }

            // 5. Record user+IP failure
            if (userKey != null) {
                recordFailure(userAttempts, userKey, userFreeAttempts, now)
            }

            // 6. If user+IP suspected -> reject
            if (userKey != null) {
                userAttempts.getIfPresent(userKey)?.let { userAttempt ->
                    if (now.isBefore(userAttempt.nextAllowed)) {
                        val remaining = Duration.between(now, userAttempt.nextAllowed).seconds
                        throw FailedLoginException("Login temporarily suspended for user '$user'. Try again in $remaining seconds.")
                    }
                }
            }
            // 7. Plain authentication failure
            throw fle
        }
    }

    private fun recordFailure(
            cache: Cache<String, Attempt>,
            key: String,
            freeAttempts: Int,
            now: Instant
    ) {
        val prev = cache.getIfPresent(key)
        val newCount = (prev?.count ?: 0) + 1

        val delay =
                if (newCount <= freeAttempts) 0
                else {
                    val exp = newCount - freeAttempts - 1
                    (baseDelaySeconds * 2.0.pow(exp)).toLong().coerceAtMost(maxDelaySeconds)
                }
        cache.put(key, Attempt(newCount, now.plusSeconds(delay)))
    }

    private fun extractIp(remotingConnection: RemotingConnection?): String {
        val raw = remotingConnection?.remoteAddress ?: "unknown"
        return raw.substringAfter('/').substringBefore(':')
    }

    private fun hash(value: String): String {
        return Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        )
    }
}