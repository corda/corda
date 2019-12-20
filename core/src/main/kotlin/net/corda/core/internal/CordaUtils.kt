package net.corda.core.internal

import net.corda.core.DeleteForDJVM
import net.corda.core.contracts.ContractClassName
import net.corda.core.cordapp.CordappProvider
import net.corda.core.flows.DataVendingFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.node.NetworkParameters
import net.corda.core.node.ServicesForResolution
import net.corda.core.node.ZoneVersionTooLowException
import net.corda.core.node.services.AttachmentId
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializationContext
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import org.slf4j.MDC
import java.security.PublicKey

// *Internal* Corda-specific utilities.

const val PLATFORM_VERSION = 6

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

// JDK11: revisit (JDK 9+ uses different numbering scheme: see https://docs.oracle.com/javase/9/docs/api/java/lang/Runtime.Version.html)
@Throws(NumberFormatException::class)
fun getJavaUpdateVersion(javaVersion: String): Long = javaVersion.substringAfter("_").substringBefore("-").toLong()

/** Provide access to internal method for AttachmentClassLoaderTests. */
@DeleteForDJVM
fun TransactionBuilder.toWireTransaction(services: ServicesForResolution, serializationContext: SerializationContext): WireTransaction {
    return toWireTransactionWithContext(services, serializationContext)
}

/** Provide access to internal method for AttachmentClassLoaderTests. */
@DeleteForDJVM
fun TransactionBuilder.toLedgerTransaction(services: ServicesForResolution, serializationContext: SerializationContext): LedgerTransaction {
    return toLedgerTransactionWithContext(services, serializationContext)
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

/**
 * @return The set of [AttachmentId]s after the node's fix-up rules have been applied to [attachmentIds].
 */
fun CordappProvider.internalFixupAttachmentIds(attachmentIds: Collection<AttachmentId>): Set<AttachmentId> {
    return (this as CordappFixupInternal).fixupAttachmentIds(attachmentIds)
}
