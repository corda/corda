package net.corda.node.services.messaging

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.core.utilities.loggerFor
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
import net.corda.node.services.config.SecurityConfiguration.AuthService.Options.RateLimit

class RateLimitingActiveMQJAASSecurityManager(
        configurationName: String,
        configuration: SecurityConfiguration,
        rateLimitConfig: RateLimit?
) : ActiveMQJAASSecurityManager(configurationName, configuration) {

    companion object {
        private val log = loggerFor<RateLimitingActiveMQJAASSecurityManager>()
    }

    private data class Attempt(val count: Int, val nextAllowed: Instant) {
        fun suspended(now: Instant): Boolean = now.isBefore(nextAllowed)
        fun remainingSeconds(now: Instant): Long = Duration.between(now, nextAllowed).seconds
    }

    private val baseDelaySeconds = rateLimitConfig?.backoffBaseSeconds ?: 2L
    private val maxDelaySeconds = rateLimitConfig?.backoffMaxSeconds ?: 60L
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
                    .expireAfterWrite(attemptExpireMinutes, TimeUnit.MINUTES)
                    .maximumSize(10_000)
                    .build<String, Attempt>()

    @Suppress("ComplexMethod")
    override fun authenticate(user: String?, password: String?, remotingConnection: RemotingConnection?, securityDomain: String?): Subject? {

        val now = Instant.now()
        val ip = extractIp(remotingConnection)
        val userKey = user?.let { hash("$it|$ip") }
        val ipKey = hash(ip)

        // 1. If user+IP suspended -> immediately reject
        if (userKey != null) {
            userAttempts.getIfPresent(userKey)?.let { userAttempt ->
                if (userAttempt.suspended(now)) {
                    val remaining = userAttempt.remainingSeconds(now)
                    // additional logging because Artemis will swallow the FailedLoginExceptions thrown in this method
                    // and wrap them into ActiveMQInternalErrorException without the cause
                    log.warn(printWarnMessage(user, remaining))
                    throw FailedLoginException(printWarnMessage(user, remaining))
                }
            }
        }

        // 2. Attempt authentication
        val subject = super.authenticate(user, password, remotingConnection, securityDomain)
        if (subject != null) {
            // success - clear user cache only
            if (userKey != null) {
                userAttempts.invalidate(userKey)
            }
            return subject
        }

        // 3. Record IP failure
        recordFailure(ipAttempts, ipKey, ipFreeAttempts, now)

        // 4, If IP suspended -> reject
        ipAttempts.getIfPresent(ipKey)?.let { ipAttempt ->
            if (ipAttempt.suspended(now)) {
                val remaining = ipAttempt.remainingSeconds(now)
                log.warn(printWarnMessage(ip, remaining, true))
                throw FailedLoginException(printWarnMessage(ip, remaining, true))
            }
        }

        // 5. Record user+IP failure
        if (userKey != null) {
            recordFailure(userAttempts, userKey, userFreeAttempts, now)
        }

        // 6. Re-check if user+IP is suspended after recording failure in case this attempt triggered suspension
        if (userKey != null) {
            userAttempts.getIfPresent(userKey)?.let { userAttempt ->
                if (userAttempt.suspended(now)) {
                    val remaining = userAttempt.remainingSeconds(now)
                    log.warn(printWarnMessage(user, remaining))
                    throw FailedLoginException(printWarnMessage(user, remaining))
                }
            }
        }

        // 7. Plain authentication failure
        return null
    }

    private fun recordFailure(
            cache: Cache<String, Attempt>,
            key: String,
            freeAttempts: Int,
            now: Instant
    ) {
        cache.asMap().compute(key) { _, prev ->
            val newCount = (prev?.count ?: 0) + 1
            val delay = if (newCount <= freeAttempts) -60L // negative to indicate no suspension
            else (baseDelaySeconds * 2.0.pow(newCount - freeAttempts - 1)).toLong().coerceAtMost(maxDelaySeconds)
            Attempt(newCount, now.plusSeconds(delay))
        }
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

    private fun printWarnMessage(userOrIp: String, remaining: Long, isIp: Boolean = false): String {
        return if (isIp) "Login temporarily suspended for IP '$userOrIp' due to too many failed attempts. Try again in $remaining seconds."
        else "Login temporarily suspended for user '$userOrIp' due to too many failed attempts. Try again in $remaining seconds."
    }
}