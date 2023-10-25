package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.CordaInternal
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.ProgressTracker

/**
 * Ledger Recovery Flow (available in Enterprise only).
 */
@StartableByRPC
class LedgerRecoveryFlow(
        private val parameters: LedgerRecoveryParameters,
        override val progressTracker: ProgressTracker = ProgressTracker()) : FlowLogic<LedgerRecoveryResult>() {

    @CordaInternal
    data class ExtraConstructorArgs(val parameters: LedgerRecoveryParameters)
    @CordaInternal
    fun getExtraConstructorArgs() = ExtraConstructorArgs(parameters)

    @Suspendable
    @Throws(LedgerRecoveryException::class)
    override fun call(): LedgerRecoveryResult {
        throw NotImplementedError("Enterprise only feature")
    }
}

@CordaSerializable
class LedgerRecoveryException(message: String) : FlowException("Ledger recovery failed: $message")

@CordaSerializable
data class LedgerRecoveryParameters(
    val recoveryPeers: Collection<Party>,
    val timeWindow: RecoveryTimeWindow? = null,
    val useAllNetworkNodes: Boolean = false,
    val dryRun: Boolean = false,
    val useTimeWindowNarrowing: Boolean = true,
    val verboseLogging: Boolean = false,
    val recoveryBatchSize: Int = 1000,
    val alsoFinalize: Boolean = false
)

@CordaSerializable
data class LedgerRecoveryResult(
    val totalRecoveredRecords: Long,
    val totalRecoveredTransactions: Long,
    val totalRecoveredInFlightTransactions: Long,
    val totalErrors: Long
)
