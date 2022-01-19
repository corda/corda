package net.corda.core.internal.rules

import net.corda.core.contracts.ContractState
import net.corda.core.internal.PlatformVersionSwitches
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
object StateContractValidationEnforcementRule {

    private val logger = LoggerFactory.getLogger(StateContractValidationEnforcementRule::class.java)

    private val targetVersionCache = ConcurrentHashMap<URL, Int>()

    fun shouldEnforce(state: ContractState): Boolean {
        val jarLocation = state::class.java.protectionDomain.codeSource.location

        if (jarLocation == null) {
            logger.warnOnce("""
                Unable to determine JAR location for contract state class ${state::class.java.name},
                and consequently unable to determine target platform version.
                Enforcing state/contract agreement validation by default.
            """.trimIndent().replace("\n", " "))
            return true
        }

        val targetVersion = targetVersionCache.computeIfAbsent(jarLocation) {
            jarLocation.openStream().use { inputStream ->
                JarInputStream(inputStream).manifest?.targetPlatformVersion ?: 1
            }
        }

        return targetVersion >= PlatformVersionSwitches.BELONGS_TO_CONTRACT_ENFORCEMENT
    }
}