package net.corda.core.internal.services

import net.corda.core.crypto.SecureHash
import net.corda.core.node.ServiceHub
import net.corda.core.node.ServicesForResolution
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction

/**
 * All [ServiceHub]s must implement [VerifyingServiceHub] so that they can resolve [LedgerTransaction]s and verify [SignedTransaction]s.
 * [SignedTransaction.verify] and [SignedTransaction.toLedgerTransaction] will convert the service instance using [asVerifying].
 *
 * @see SignedTransaction.verifyInternal
 * @see SignedTransaction.toLedgerTransactionInternal
 */
interface VerifyingServiceHub : ServiceHub, ServicesForResolutionInternal, VerificationSupport {
    override fun getSignedTransaction(id: SecureHash): SignedTransaction? = validatedTransactions.getTransaction(id)
}

fun ServiceHub.asVerifying(): VerifyingServiceHub = this as? VerifyingServiceHub ?: mockServicesVerifyingView

// MockServices does not implement VerifyingServiceHub as it's public API. Doing so would expose the internal API in VerifyingServiceHub.
// Instead it has a VerifyingServiceHub "view" which we get at via reflection.
val ServicesForResolution.mockServicesVerifyingView: VerifyingServiceHub get() {
    var clazz: Class<*> = javaClass
    while (true) {
        if (clazz.name == "net.corda.testing.node.MockServices") {
            return clazz.getDeclaredMethod("getVerifyingView").apply { isAccessible = true }.invoke(this) as VerifyingServiceHub
        }
        clazz = clazz.superclass ?: throw ClassCastException("${javaClass.name} is not a VerifyingServiceHub")
    }
}
