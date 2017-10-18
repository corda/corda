package net.corda.observerdemo.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ObservedState
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.observerdemo.Observed
import java.security.PublicKey
import java.security.SignatureException

object RegistryObserverFlow {
    /**
     * A flow to be used for finalising a trade finance registry transaction, by sending the transaction to the
     * registry to observe. The registry then signs the transaction before sending it to the uniqueness service to ensure
     * atomicity of updates. If the uniqueness service accepts and signs the transaction, the registry records the update
     * then returns both its and the uniqueness service's signatures. Finally the client assembles the complete transaction,
     * records it in the local vault and return it.
     *
     * @return the complete signed transaction.
     */
    @InitiatingFlow
    @StartableByRPC
    class Client(private val stx: SignedTransaction,
                 override val progressTracker: ProgressTracker = Client.tracker()) : FlowLogic<SignedTransaction>() {

        companion object {
            object REQUESTING : ProgressTracker.Step("Requesting signature by registry service")
            object VERIFYING : ProgressTracker.Step("Verifying response from registry service")

            fun tracker() = ProgressTracker(REQUESTING, VERIFYING)
        }

        @Suspendable
        override fun call(): SignedTransaction {
            progressTracker.currentStep = REQUESTING

            // Ensure the transaction is sane first
            stx.tx.toLedgerTransaction(serviceHub).verify()

            // Determine the observer
            val observableStates: Collection<ObservedState> = stx.tx.inputs
                    .map { stateRef -> serviceHub.loadState(stateRef).data }
                    .filterIsInstance<ObservedState>() + stx.tx.outputs
                    .map { it.data }
                    .filterIsInstance<ObservedState>()
            val observers = observableStates.flatMap(ObservedState::observers).toSet()
            require(observers.isNotEmpty()) { "No observers are specified for transaction ${stx.id}" }
            val observerParty: AbstractParty = observers.singleOrNull() ?: throw IllegalArgumentException("Multiple observers specified for transaction ${stx.id}")

            val session = initiateFlow(serviceHub.identityService.requireWellKnownPartyFromAnonymous(observerParty))
            val response = session.sendAndReceive<Result>(stx)

            progressTracker.currentStep = VERIFYING

            return response.unwrap {
                when (it) {
                    is Result.Success -> {
                        val possiblyCompleteTransaction = it.stx
                        try {
                            possiblyCompleteTransaction.verifyRequiredSignatures()
                        } catch (ex: SignedTransaction.SignaturesMissingException) {
                            throw RegistryException(Error.SignaturesMissing(ex.missing))
                        } catch (ex: SignatureException) {
                            throw RegistryException(Error.TransactionInvalid())
                        }
                        serviceHub.recordTransactions(setOf(possiblyCompleteTransaction))
                        possiblyCompleteTransaction
                    }
                    is Result.Error -> throw RegistryException(it.error)
                }
            }
        }
    }

    /**
     * Checks that transaction is valid, contains anything of interest to us (otherwise what's the point), before
     * signing the transaction. The transaction is then relayed to the notary for signing. If the notary signs,
     * we update our records then pass the completed transaction back to the client.
     */
    @InitiatedBy(Client::class)
    open class Service(val otherSide: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val stx = otherSide.receive<SignedTransaction>().unwrap { it }
            val wtx = stx.tx

            val result = try {
                val observedCommand = wtx.commands.singleOrNull(){ it.value is Observed } ?: throw RegistryException(Error.NothingToObserve())
                // Checks that we're the correct registry are handled by the contract, so the transaction will not
                // verify if it doesn't match.
                val stxWithOurs = serviceHub.addSignature(stx, serviceHub.keyManagementService.filterMyKeys(observedCommand.signers).single())
                val notarySig = subFlow(NotaryFlow.Client(stxWithOurs))
                val completeTx = stxWithOurs.withAdditionalSignatures(notarySig)
                serviceHub.recordTransactions(listOf(completeTx))
                Result.noError(completeTx)
            } catch (e: RegistryException) {
                Result.withError(e.error)
            }

            otherSide.send(result)
        }
    }

    @CordaSerializable
    sealed class Result() {
        companion object {
            fun withError(error: RegistryObserverFlow.Error) = Error(error)
            fun noError(stx: SignedTransaction) = Success(stx)
        }

        class Error(val error: RegistryObserverFlow.Error) : Result()
        class Success(val stx: SignedTransaction) : Result()
    }

    @CordaSerializable
    sealed class Error {
        class NothingToObserve : Error()
        class TransactionInvalid(val message: String? = null) : Error()

        class SignaturesMissing(val missingSigners: Set<PublicKey>) : Error()
    }
}
