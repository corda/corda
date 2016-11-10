package net.corda.node.internal

import net.corda.contracts.asset.Cash
import net.corda.core.contracts.*
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.toStringShort
import net.corda.core.node.NodeInfo
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.node.services.StateMachineTransactionMapping
import net.corda.core.node.services.Vault
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.node.services.messaging.*
import net.corda.node.services.statemachine.StateMachineManager
import net.corda.node.utilities.databaseTransaction
import net.corda.protocols.BroadcastTransactionProtocol
import net.corda.protocols.FinalityProtocol
import org.jetbrains.exposed.sql.Database
import rx.Observable
import java.security.KeyPair

/**
 * Server side implementations of RPCs available to MQ based client tools. Execution takes place on the server
 * thread (i.e. serially). Arguments are serialised and deserialised automatically.
 */
class CordaRPCOpsImpl(
        val services: ServiceHub,
        val smm: StateMachineManager,
        val database: Database
) : CordaRPCOps {
    companion object {
        const val CASH_PERMISSION = "CASH"
    }

    override val protocolVersion: Int get() = 0

    override fun networkMapUpdates(): Pair<List<NodeInfo>, Observable<NetworkMapCache.MapChange>> {
        return services.networkMapCache.track()
    }

    override fun vaultAndUpdates(): Pair<List<StateAndRef<ContractState>>, Observable<Vault.Update>> {
        return databaseTransaction(database) {
            val (vault, updates) = services.vaultService.track()
            Pair(vault.states.toList(), updates)
        }
    }
    override fun verifiedTransactions(): Pair<List<SignedTransaction>, Observable<SignedTransaction>> {
        return databaseTransaction(database) {
            services.storageService.validatedTransactions.track()
        }
    }

    override fun stateMachinesAndUpdates(): Pair<List<StateMachineInfo>, Observable<StateMachineUpdate>> {
        val (allStateMachines, changes) = smm.track()
        return Pair(
                allStateMachines.map { StateMachineInfo.fromProtocolStateMachineImpl(it) },
                changes.map { StateMachineUpdate.fromStateMachineChange(it) }
        )
    }

    override fun stateMachineRecordedTransactionMapping(): Pair<List<StateMachineTransactionMapping>, Observable<StateMachineTransactionMapping>> {
        return databaseTransaction(database) {
            services.storageService.stateMachineRecordedTransactionMapping.track()
        }
    }

    override fun executeCommand(command: ClientToServiceCommand): TransactionBuildResult {
        requirePermission(CASH_PERMISSION)
        return databaseTransaction(database) {
            when (command) {
                is ClientToServiceCommand.IssueCash -> issueCash(command)
                is ClientToServiceCommand.PayCash -> initiatePayment(command)
                is ClientToServiceCommand.ExitCash -> exitCash(command)
            }
        }
    }

    override fun nodeIdentity(): NodeInfo {
        return services.myInfo
    }

    override fun addVaultTransactionNote(txnId: SecureHash, txnNote: String) {
        return databaseTransaction(database) {
           services.vaultService.addNoteToTransaction(txnId, txnNote)
        }
    }

    override fun getVaultTransactionNotes(txnId: SecureHash): Iterable<String> {
        return databaseTransaction(database) {
            services.vaultService.getTransactionNotes(txnId)
        }
    }

    // TODO: Make a lightweight protocol that manages this workflow, rather than embedding it directly in the service
    private fun initiatePayment(req: ClientToServiceCommand.PayCash): TransactionBuildResult {
        val builder: TransactionBuilder = TransactionType.General.Builder(null)
        // TODO: Have some way of restricting this to states the caller controls
        try {
            val (spendTX, keysForSigning) = services.vaultService.generateSpend(builder,
                    req.amount.withoutIssuer(), req.recipient.owningKey, setOf(req.amount.token.issuer.party))

            keysForSigning.forEach {
                val key = services.keyManagementService.keys[it] ?: throw IllegalStateException("Could not find signing key for ${it.toStringShort()}")
                builder.signWith(KeyPair(it, key))
            }

            val tx = spendTX.toSignedTransaction(checkSufficientSignatures = false)
            val protocol = FinalityProtocol(tx, setOf(req), setOf(req.recipient))
            return TransactionBuildResult.ProtocolStarted(
                    smm.add(protocol).id,
                    tx,
                    "Cash payment transaction generated"
            )
        } catch(ex: InsufficientBalanceException) {
            return TransactionBuildResult.Failed(ex.message ?: "Insufficient balance")
        }
    }

    // TODO: Make a lightweight protocol that manages this workflow, rather than embedding it directly in the service
    private fun exitCash(req: ClientToServiceCommand.ExitCash): TransactionBuildResult {
        val builder: TransactionBuilder = TransactionType.General.Builder(null)
        try {
            val issuer = PartyAndReference(services.myInfo.legalIdentity, req.issueRef)
            Cash().generateExit(builder, req.amount.issuedBy(issuer),
                    services.vaultService.currentVault.statesOfType<Cash.State>().filter { it.state.data.owner == issuer.party.owningKey })
            val myKey = services.legalIdentityKey
            builder.signWith(myKey)

            // Work out who the owners of the burnt states were
            val inputStatesNullable = services.vaultService.statesForRefs(builder.inputStates())
            val inputStates = inputStatesNullable.values.filterNotNull().map { it.data }
            if (inputStatesNullable.size != inputStates.size) {
                val unresolvedStateRefs = inputStatesNullable.filter { it.value == null }.map { it.key }
                throw InputStateRefResolveFailed(unresolvedStateRefs)
            }

            // TODO: Is it safe to drop participants we don't know how to contact? Does not knowing how to contact them
            //       count as a reason to fail?
            val participants: Set<Party> = inputStates.filterIsInstance<Cash.State>().map { services.identityService.partyFromKey(it.owner) }.filterNotNull().toSet()

            // Commit the transaction
            val tx = builder.toSignedTransaction(checkSufficientSignatures = false)
            val protocol = FinalityProtocol(tx, setOf(req), participants)
            return TransactionBuildResult.ProtocolStarted(
                    smm.add(protocol).id,
                    tx,
                    "Cash destruction transaction generated"
            )
        } catch (ex: InsufficientBalanceException) {
            return TransactionBuildResult.Failed(ex.message ?: "Insufficient balance")
        }
    }

    // TODO: Make a lightweight protocol that manages this workflow, rather than embedding it directly in the service
    private fun issueCash(req: ClientToServiceCommand.IssueCash): TransactionBuildResult {
        val builder: TransactionBuilder = TransactionType.General.Builder(notary = null)
        val issuer = PartyAndReference(services.myInfo.legalIdentity, req.issueRef)
        Cash().generateIssue(builder, req.amount.issuedBy(issuer), req.recipient.owningKey, req.notary)
        val myKey = services.legalIdentityKey
        builder.signWith(myKey)
        val tx = builder.toSignedTransaction(checkSufficientSignatures = true)
        // Issuance transactions do not need to be notarised, so we can skip directly to broadcasting it
        val protocol = BroadcastTransactionProtocol(tx, setOf(req), setOf(req.recipient))
        return TransactionBuildResult.ProtocolStarted(
                smm.add(protocol).id,
                tx,
                "Cash issuance completed"
        )
    }

    class InputStateRefResolveFailed(stateRefs: List<StateRef>) :
            Exception("Failed to resolve input StateRefs $stateRefs")
}
