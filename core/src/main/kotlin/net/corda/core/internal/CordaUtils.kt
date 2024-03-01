@file:Suppress("MatchingDeclarationName")
package net.corda.core.internal

import net.corda.core.contracts.ContractClassName
import net.corda.core.contracts.TransactionResolutionException
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.DataVendingFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.node.NetworkParameters
import net.corda.core.node.ServiceHub
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.ZoneVersionTooLowException
import net.corda.core.node.services.TransactionStorage
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import org.slf4j.MDC
import java.security.PublicKey

// *Internal* Corda-specific utilities.


// When incrementing platformVersion make sure to update PLATFORM_VERSION in constants.properties as well.
const val PLATFORM_VERSION = 140

fun ServicesForResolution.ensureMinimumPlatformVersion(requiredMinPlatformVersion: Int, feature: String) {
    checkMinimumPlatformVersion(networkParameters.minimumPlatformVersion, requiredMinPlatformVersion, feature)
}

fun checkMinimumPlatformVersion(minimumPlatformVersion: Int, requiredMinPlatformVersion: Int, feature: String) {
    if (minimumPlatformVersion < requiredMinPlatformVersion) {
        throw ZoneVersionTooLowException(
                "$feature requires all nodes on the Corda compatibility zone to be running at least platform version " +
                        "$requiredMinPlatformVersion. The current zone is only enforcing a minimum platform version of " +
                        "$minimumPlatformVersion. Please contact your zone operator."
        )
    }
}

/** Checks if this flow is an idempotent flow. */
fun Class<out FlowLogic<*>>.isIdempotentFlow(): Boolean {
    return IdempotentFlow::class.java.isAssignableFrom(this)
}

/**
 * Ensures each log entry from the current thread will contain id of the transaction in the MDC.
 */
internal fun SignedTransaction.pushToLoggingContext() {
    MDC.put("tx_id", id.toString())
}

private fun isPackageValid(packageName: String): Boolean {
    return packageName.isNotEmpty() &&
            !packageName.endsWith(".") &&
            packageName.split(".").all { token ->
                Character.isJavaIdentifierStart(token[0]) && token.toCharArray().drop(1).all { Character.isJavaIdentifierPart(it) }
            }
}

/** Check if a string is a legal Java package name. */
fun requirePackageValid(name: String) {
    require(isPackageValid(name)) { "Invalid Java package name: `$name`." }
}

/**
 * This is a wildcard payload to be used by the invoker of the [DataVendingFlow] to allow unlimited access to its vault.
 *
 * TODO Fails with a serialization exception if it is not a list. Why?
 */
@CordaSerializable
object RetrieveAnyTransactionPayload : ArrayList<Any>()

/**
 * Returns true if the [fullClassName] is in a subpackage of [packageName].
 * E.g.: "com.megacorp" owns "com.megacorp.tokens.MegaToken"
 *
 * Note: The ownership check is ignoring case to prevent people from just releasing a jar with: "com.megaCorp.megatoken" and pretend they are MegaCorp.
 * By making the check case insensitive, the node will require that the jar is signed by MegaCorp, so the attack fails.
 */
private fun owns(packageName: String, fullClassName: String): Boolean = fullClassName.startsWith("$packageName.", ignoreCase = true)

/** Returns the public key of the package owner of the [contractClassName], or null if not owned. */
fun NetworkParameters.getPackageOwnerOf(contractClassName: ContractClassName): PublicKey? {
    return packageOwnership.entries.singleOrNull { owns(it.key, contractClassName) }?.value
}

// Make sure that packages don't overlap so that ownership is clear.
fun noPackageOverlap(packages: Collection<String>): Boolean {
    return packages.all { outer -> packages.none { inner -> inner != outer && inner.startsWith("$outer.") } }
}

fun TransactionStorage.getRequiredTransaction(txhash: SecureHash): SignedTransaction {
    return getTransaction(txhash) ?: throw TransactionResolutionException(txhash)
}

fun ServiceHub.getRequiredTransaction(txhash: SecureHash): SignedTransaction = validatedTransactions.getRequiredTransaction(txhash)
