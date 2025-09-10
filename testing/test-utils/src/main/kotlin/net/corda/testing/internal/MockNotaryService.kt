package net.corda.testing.internal

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.NotaryInstruction
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.NotarisationRequestSignature
import net.corda.core.flows.NotaryError
import net.corda.core.flows.StateConsumptionDetails
import net.corda.core.flows.StateConsumptionDetails.ConsumedStateType.INPUT_STATE
import net.corda.core.flows.StateConsumptionDetails.ConsumedStateType.REFERENCE_INPUT_STATE
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.asCordaFuture
import net.corda.core.internal.notary.UniquenessProvider
import net.corda.core.internal.notary.UniquenessProvider.Result
import net.corda.core.internal.notary.validateTimeWindow
import net.corda.node.services.api.ServiceHubInternal
import net.corda.notary.common.BatchSigningFunction
import net.corda.notary.common.ValidationModeNotaryService
import net.corda.notary.common.signBatch
import java.security.PublicKey
import java.time.Clock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class MockNotaryService(services: ServiceHubInternal, notaryIdentityKey: PublicKey)
    : ValidationModeNotaryService(services, notaryIdentityKey) {
    override val uniquenessProvider = MockUniquenessProvider(services.clock) { signBatch(it, notaryIdentityKey, services) }

    override fun start() {
    }

    override fun stop() {
        uniquenessProvider.close()
    }
}

class MockUniquenessProvider(private val clock: Clock,
                             private val signTransactions: BatchSigningFunction) : UniquenessProvider, AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor()
    private val spentStateRefs = HashMap<StateRef, SecureHash>()
    private val seenTxIds = HashSet<SecureHash>()

    val forcedNextResult = AtomicReference<Result>()

    override fun commit(states: List<StateRef>,
                        txId: SecureHash,
                        callerIdentity: Party,
                        requestSignature: NotarisationRequestSignature,
                        timeWindow: TimeWindow?,
                        references: List<StateRef>,
                        notaryInstructions: List<NotaryInstruction>): CordaFuture<Result> {
        val future = CompletableFuture.supplyAsync(
                { doCommit(txId, states, references, timeWindow) },
                executor
        )
        return future
                .exceptionally { e -> Result.Failure(NotaryError.General(e)) }
                .asCordaFuture()
    }

    private fun doCommit(txId: SecureHash, inputs: List<StateRef>, references: List<StateRef>, timeWindow: TimeWindow?): Result {
        val forcedResult = forcedNextResult.getAndSet(null)
        if (forcedResult != null) {
            return forcedResult
        }
        if (txId in seenTxIds) {
            return signNotarisation(txId)
        }
        val consumedStates = LinkedHashMap<StateRef, StateConsumptionDetails>()
        for ((stateRefs, stateType) in listOf(inputs to INPUT_STATE, references to REFERENCE_INPUT_STATE)) {
            for (stateRef in stateRefs) {
                val consumingTxId = spentStateRefs[stateRef]
                if (consumingTxId != null) {
                    consumedStates[stateRef] = StateConsumptionDetails(consumingTxId.reHash(), stateType)
                }
            }
        }
        if (consumedStates.isNotEmpty()) {
            return Result.Failure(NotaryError.Conflict(txId, consumedStates))
        }
        val timeWindowError = validateTimeWindow(clock.instant(), timeWindow)
        if (timeWindowError != null) {
            return Result.Failure(timeWindowError)
        }
        for (input in inputs) {
            spentStateRefs[input] = txId
        }
        seenTxIds += txId
        return signNotarisation(txId)
    }

    private fun signNotarisation(txId: SecureHash): Result.Success {
        val notarySignature = signTransactions(listOf(txId)).forParticipant(txId)
        return Result.Success(notarySignature)
    }

    override fun close() {
        executor.shutdown()
    }
}
