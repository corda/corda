package net.corda.core.internal.rules

import net.corda.core.contracts.ContractState
import net.corda.core.internal.cordapp.targetPlatformVersion
import net.corda.core.internal.warnOnce
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarInputStream

// This file provides rules that depend on the targetVersion of the current Contract or Flow.
// Rules defined in this package are automatically removed from the DJVM in core-deterministic,
// and must be replaced by a deterministic alternative defined within that module.

/**
 * Rule which determines whether [ContractState]s must declare the [Contract] to which they belong (e.g. via the
 * [BelongsToContract] annotation), and must be bundled together with that contract in any [TransactionState].
 *
 * This rule is consulted during validation by [LedgerTransaction].
 */
object CordappVersionUtils {

    private val logger = LoggerFactory.getLogger(CordappVersionUtils::class.java)

    private const val NO_TARGET = -1

    private val targetVersionCache = ConcurrentHashMap<URL, Int>()

    fun shouldEnforce(state: ContractState): Boolean = getTargetVersion(state).let { it == NO_TARGET || it >= 4 }

    fun getTargetVersion(state: ContractState): Int {
        val jarLocation = state::class.java.protectionDomain.codeSource.location

        if (jarLocation == null) {
            logger.warnOnce("""
                Unable to determine JAR location for contract state class ${state::class.java.name},
                and consequently unable to determine target platform version.
                Enforcing state/contract agreement validation by default.

                For details see: https://docs.corda.net/api-contract-constraints.html#contract-state-agreement
            """.trimIndent().replace("\n", " "))
            return NO_TARGET
        }

        return targetVersionCache.computeIfAbsent(jarLocation) {
            jarLocation.openStream().use { inputStream ->
                JarInputStream(inputStream).manifest?.targetPlatformVersion ?: 1
            }
        }
    }
}