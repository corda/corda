package net.corda.core.internal.rules

import net.corda.core.contracts.ContractState
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.warnOnce
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarInputStream
import java.util.jar.Manifest

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

    private val logger = contextLogger()

    private val targetVersionCache = ConcurrentHashMap<URL, Int>()

    fun shouldEnforce(state: ContractState): Boolean {
        val jarLocation = state::class.java.protectionDomain.codeSource.location

        if (jarLocation == null) {
            logger.warnOnce("""
                Unable to determine JAR location for contract state class ${state::class.java.name},
                and consequently unable to determine target platform version.
                Enforcing state/contract agreement validation by default.

                For details see: https://docs.corda.net/api-contract-constraints.html#contract-state-agreement
            """.trimIndent().replace("\n", " "))
            return true
        }

        val targetVersion = targetVersionCache.computeIfAbsent(jarLocation) {
            jarLocation.openStream().use { inputStream ->
                JarInputStream(inputStream).manifest?.targetPlatformVersion ?: 1
            }
        }

        return targetVersion >= 4
    }
}

private val Manifest.targetPlatformVersion: Int get() {
    val minPlatformVersion = mainAttributes.getValue("Min-Platform-Version")?.toInt() ?: 1
    return mainAttributes.getValue("Target-Platform-Version")?.toInt() ?: minPlatformVersion
}