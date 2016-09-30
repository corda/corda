package com.r3corda.node.internal

import com.r3corda.contracts.asset.Cash
import com.r3corda.contracts.asset.InsufficientBalanceException
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.toStringShort
import com.r3corda.core.node.ServiceHub
import com.r3corda.core.node.services.Vault
import com.r3corda.core.transactions.TransactionBuilder
import com.r3corda.node.services.api.ServiceHubInternal
import com.r3corda.node.services.messaging.CordaRPCOps
import com.r3corda.node.services.messaging.StateMachineInfo
import com.r3corda.node.services.messaging.StateMachineUpdate
import com.r3corda.node.services.messaging.TransactionBuildResult
import com.r3corda.node.services.statemachine.StateMachineManager
import com.r3corda.node.utilities.databaseTransaction
import com.r3corda.protocols.BroadcastTransactionProtocol
import com.r3corda.protocols.FinalityProtocol
import org.jetbrains.exposed.sql.Database
import rx.Observable
import java.security.KeyPair

/**
 * Server side implementations of RPCs available to MQ based client tools. Execution takes place on the server
 * thread (i.e. serially). Arguments are serialised and deserialised automatically.
 */
class ServerRPCOps(
        val services: ServiceHub,
        val smm: StateMachineManager,
        val database: Database
) : CordaRPCOps {
    override val protocolVersion: Int = 0

    override fun vaultAndUpdates(): Pair<List<StateAndRef<ContractState>>, Observable<Vault.Update>> {
        return databaseTransaction(database) {
            val (vault, updates) = services.vaultService.track()
            Pair(vault.states.toList(), updates)
        }
    }
    override fun verifiedTransactions() = services.storageService.validatedTransactions.track()
    override fun stateMachinesAndUpdates(): Pair<List<StateMachineInfo>, Observable<StateMachineUpdate>> {
        val (allStateMachines, changes) = smm.track()
        return Pair(
                allStateMachines.map { StateMachineInfo.fromProtocolStateMachineImpl(it) },
                changes.map { StateMachineUpdate.fromStateMachineChange(it) }
        )
    }
    override fun stateMachineRecordedTransactionMapping() = services.storageService.stateMachineRecordedTransactionMapping.track()

    override fun executeCommand(command: ClientToServiceCommand): TransactionBuildResult {
        return databaseTransaction(database) {
            when (command) {
                is ClientToServiceCommand.IssueCash -> issueCash(command)
                is ClientToServiceCommand.PayCash -> initiatePayment(command)
                is ClientToServiceCommand.ExitCash -> exitCash(command)
            }
        }
    }

    // TODO: Make a lightweight protocol that manages this workflow, rather than embedding it directly in the service
    private fun initiatePayment(req: ClientToServiceCommand.PayCash): TransactionBuildResult {
        val builder: TransactionBuilder = TransactionType.General.Builder(null)
        // TODO: Have some way of restricting this to states the caller controls
        try {
            val vaultCashStates = services.vaultService.currentVault.statesOfType<Cash.State>()
            // TODO: Move cash state filtering by issuer down to the contract itself
            val cashStatesOfRightCurrency = vaultCashStates.filter { it.state.data.amount.token == req.amount.token }
            val keysForSigning = Cash().generateSpend(
                    tx = builder,
                    amount = req.amount.withoutIssuer(),
                    to = req.recipient.owningKey,
                    assetsStates = cashStatesOfRightCurrency,
                    onlyFromParties = setOf(req.amount.token.issuer.party)
            )
            keysForSigning.forEach {
                val key = services.keyManagementService.keys[it] ?: throw IllegalStateException("Could not find signing key for ${it.toStringShort()}")
                builder.signWith(KeyPair(it, key))
            }
            val tx = builder.toSignedTransaction(checkSufficientSignatures = false)
            val protocol = FinalityProtocol(tx, setOf(req), setOf(req.recipient))
            return TransactionBuildResult.ProtocolStarted(
                    smm.add(BroadcastTransactionProtocol.TOPIC, protocol).id,
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
            val issuer = PartyAndReference(services.storageService.myLegalIdentity, req.issueRef)
            Cash().generateExit(builder, req.amount.issuedBy(issuer),
                    services.vaultService.currentVault.statesOfType<Cash.State>().filter { it.state.data.owner == issuer.party.owningKey })
            builder.signWith(services.storageService.myLegalIdentityKey)

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
                    smm.add(BroadcastTransactionProtocol.TOPIC, protocol).id,
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
        val issuer = PartyAndReference(services.storageService.myLegalIdentity, req.issueRef)
        Cash().generateIssue(builder, req.amount.issuedBy(issuer), req.recipient.owningKey, req.notary)
        builder.signWith(services.storageService.myLegalIdentityKey)
        val tx = builder.toSignedTransaction(checkSufficientSignatures = true)
        // Issuance transactions do not need to be notarised, so we can skip directly to broadcasting it
        val protocol = BroadcastTransactionProtocol(tx, setOf(req), setOf(req.recipient))
        return TransactionBuildResult.ProtocolStarted(
                smm.add(BroadcastTransactionProtocol.TOPIC, protocol).id,
                tx,
                "Cash issuance completed"
        )
    }

    class InputStateRefResolveFailed(stateRefs: List<StateRef>) :
            Exception("Failed to resolve input StateRefs $stateRefs")

}
