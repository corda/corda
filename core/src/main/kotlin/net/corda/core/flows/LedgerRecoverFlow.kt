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

    // constructors added to aid Corda Node Shell flow command invocation
    constructor(recoveryPeer: Party) : this(LedgerRecoveryParameters(setOf(recoveryPeer)))
    constructor(recoveryPeers: Collection<Party>) : this(LedgerRecoveryParameters(recoveryPeers))
    constructor(useAllNetworkNodes: Boolean) : this(LedgerRecoveryParameters(emptySet(), useAllNetworkNodes = useAllNetworkNodes))
    constructor(recoveryPeer: Party, timeWindow: RecoveryTimeWindow) :
            this(LedgerRecoveryParameters(setOf(recoveryPeer), timeWindow))
    constructor(recoveryPeer: Party, timeWindow: RecoveryTimeWindow, dryRun: Boolean) :
            this(LedgerRecoveryParameters(setOf(recoveryPeer), timeWindow, dryRun = dryRun))
    constructor(recoveryPeer: Party, timeWindow: RecoveryTimeWindow, dryRun: Boolean, verboseLogging: Boolean) :
            this(LedgerRecoveryParameters(setOf(recoveryPeer), timeWindow, dryRun = dryRun, verboseLogging = verboseLogging))
    constructor(recoveryPeer: Party, timeWindow: RecoveryTimeWindow, dryRun: Boolean, verboseLogging: Boolean, alsoFinalize: Boolean) :
            this(LedgerRecoveryParameters(setOf(recoveryPeer), timeWindow, dryRun = dryRun, verboseLogging = verboseLogging, alsoFinalize = alsoFinalize))
    constructor(recoveryPeers: Collection<Party>, timeWindow: RecoveryTimeWindow) :
            this(LedgerRecoveryParameters(recoveryPeers, timeWindow))
    constructor(recoveryPeers: Collection<Party>, timeWindow: RecoveryTimeWindow, dryRun: Boolean) :
            this(LedgerRecoveryParameters(recoveryPeers, timeWindow, dryRun = dryRun))
    constructor(recoveryPeers: Collection<Party>, timeWindow: RecoveryTimeWindow, dryRun: Boolean, verboseLogging: Boolean) :
            this(LedgerRecoveryParameters(recoveryPeers, timeWindow, dryRun = dryRun, verboseLogging = verboseLogging))
    constructor(recoveryPeers: Collection<Party>, timeWindow: RecoveryTimeWindow, dryRun: Boolean, verboseLogging: Boolean, alsoFinalize: Boolean) :
            this(LedgerRecoveryParameters(recoveryPeers, timeWindow, dryRun = dryRun, verboseLogging = verboseLogging, alsoFinalize = alsoFinalize))
    constructor(useAllNetworkNodes: Boolean, timeWindow: RecoveryTimeWindow) :
            this(LedgerRecoveryParameters(emptySet(), timeWindow, useAllNetworkNodes = useAllNetworkNodes))
    constructor(useAllNetworkNodes: Boolean, timeWindow: RecoveryTimeWindow, dryRun: Boolean) :
            this(LedgerRecoveryParameters(emptySet(), timeWindow, useAllNetworkNodes = useAllNetworkNodes, dryRun = dryRun))
    constructor(useAllNetworkNodes: Boolean, timeWindow: RecoveryTimeWindow, dryRun: Boolean, verboseLogging: Boolean) :
            this(LedgerRecoveryParameters(emptySet(), timeWindow, useAllNetworkNodes = useAllNetworkNodes, dryRun = dryRun, verboseLogging = verboseLogging))
    constructor(useAllNetworkNodes: Boolean, timeWindow: RecoveryTimeWindow, dryRun: Boolean, verboseLogging: Boolean, recoveryBatchSize: Int, alsoFinalize: Boolean) :
            this(LedgerRecoveryParameters(emptySet(), timeWindow, useAllNetworkNodes = useAllNetworkNodes, dryRun = dryRun, verboseLogging = verboseLogging, recoveryBatchSize = recoveryBatchSize, alsoFinalize = alsoFinalize))
    constructor(useAllNetworkNodes: Boolean, timeWindow: RecoveryTimeWindow, dryRun: Boolean, verboseLogging: Boolean, recoveryBatchSize: Int) :
            this(LedgerRecoveryParameters(emptySet(), timeWindow, useAllNetworkNodes = useAllNetworkNodes, dryRun = dryRun, verboseLogging = verboseLogging, recoveryBatchSize = recoveryBatchSize))
    constructor(recoveryPeers: Collection<Party>, timeWindow: RecoveryTimeWindow, useAllNetworkNodes: Boolean, dryRun: Boolean, useTimeWindowNarrowing: Boolean, verboseLogging: Boolean, recoveryBatchSize: Int) :
            this(LedgerRecoveryParameters(recoveryPeers, timeWindow, useAllNetworkNodes,
                    dryRun = dryRun, useTimeWindowNarrowing = useTimeWindowNarrowing, verboseLogging = verboseLogging, recoveryBatchSize = recoveryBatchSize))

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
