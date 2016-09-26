package com.r3corda.node.services.monitor

import co.paralleluniverse.common.util.VisibleForTesting
import com.r3corda.contracts.asset.Cash
import com.r3corda.contracts.asset.InsufficientBalanceException
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.toStringShort
import com.r3corda.core.messaging.MessageRecipients
import com.r3corda.core.messaging.createMessage
import com.r3corda.core.node.services.DEFAULT_SESSION_ID
import com.r3corda.core.node.services.Vault
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.serialization.serialize
import com.r3corda.core.transactions.LedgerTransaction
import com.r3corda.core.transactions.TransactionBuilder
import com.r3corda.core.utilities.loggerFor
import com.r3corda.node.services.api.AbstractNodeService
import com.r3corda.node.services.api.ServiceHubInternal
import com.r3corda.node.services.statemachine.StateMachineManager
import com.r3corda.node.utilities.AddOrRemove
import com.r3corda.protocols.BroadcastTransactionProtocol
import com.r3corda.protocols.FinalityProtocol
import java.security.KeyPair
import java.time.Instant
import java.util.*
import javax.annotation.concurrent.ThreadSafe

/**
 * Service which allows external clients to monitor the node's vault and state machine manager, as well as trigger
 * actions within the node. The service also sends requests for user input back to clients, for example to enter
 * additional information while a protocol runs, or confirm an action.
 *
 * This is intended to enable a range of tools from end user UI to ops tools which monitor health across a number of nodes.
 */
// TODO: Implement authorization controls+
// TODO: Replace this entirely with a publish/subscribe based solution on a to-be-written service (likely JMS or similar),
//       rather than implement authentication and publish/subscribe ourselves.
// TODO: Clients need to be able to indicate whether they support interactivity (no point in sending requests for input
//       to a monitoring tool)
@ThreadSafe
class NodeMonitorService(services: ServiceHubInternal, val smm: StateMachineManager) : AbstractNodeService(services) {
    companion object {
        val REGISTER_TOPIC = "platform.monitor.register"
        val DEREGISTER_TOPIC = "platform.monitor.deregister"
        val STATE_TOPIC = "platform.monitor.state_snapshot"
        val IN_EVENT_TOPIC = "platform.monitor.in"
        val OUT_EVENT_TOPIC = "platform.monitor.out"

        val logger = loggerFor<NodeMonitorService>()
    }

    val listeners: MutableSet<RegisteredListener> = HashSet()

    data class RegisteredListener(val recipients: MessageRecipients, val sessionID: Long)

    init {
        addMessageHandler(REGISTER_TOPIC) { req: RegisterRequest -> processRegisterRequest(req) }
        addMessageHandler(DEREGISTER_TOPIC) { req: DeregisterRequest -> processDeregisterRequest(req) }
        addMessageHandler(OUT_EVENT_TOPIC) { req: ClientToServiceCommandMessage -> processEventRequest(req) }

        // Notify listeners on state changes
        services.storageService.validatedTransactions.updates.subscribe { tx -> notifyTransaction(tx.tx.toLedgerTransaction(services)) }
        services.vaultService.updates.subscribe { update -> notifyVaultUpdate(update) }
        smm.changes.subscribe { change ->
            val fiberId: Long = change.third
            val logic: ProtocolLogic<*> = change.first
            val progressTracker = logic.progressTracker

            notifyEvent(ServiceToClientEvent.StateMachine(Instant.now(), fiberId, logic.javaClass.name, change.second))
            if (progressTracker != null) {
                when (change.second) {
                    AddOrRemove.ADD -> progressTracker.changes.subscribe { progress ->
                        notifyEvent(ServiceToClientEvent.Progress(Instant.now(), fiberId, progress.toString()))
                    }
                    AddOrRemove.REMOVE -> {
                        // Nothing to do
                    }
                }
            }
        }
    }

    @VisibleForTesting
    internal fun notifyVaultUpdate(update: Vault.Update)
            = notifyEvent(ServiceToClientEvent.OutputState(Instant.now(), update.consumed, update.produced))

    @VisibleForTesting
    internal fun notifyTransaction(transaction: LedgerTransaction)
        = notifyEvent(ServiceToClientEvent.Transaction(Instant.now(), transaction))

    private fun processEventRequest(reqMessage: ClientToServiceCommandMessage) {
        val req = reqMessage.command
        val result: TransactionBuildResult? =
                try {
                    when (req) {
                        is ClientToServiceCommand.IssueCash -> issueCash(req)
                        is ClientToServiceCommand.PayCash -> initiatePayment(req)
                        is ClientToServiceCommand.ExitCash -> exitCash(req)
                        else -> throw IllegalArgumentException("Unknown request type ${req.javaClass.name}")
                    }
                } catch(ex: Exception) {
                    logger.warn("Exception while processing message of type ${req.javaClass.simpleName}", ex)
                    TransactionBuildResult.Failed(ex.message)
                }

        // Send back any result from the event. Not all events (especially TransactionInput) produce a
        // result.
        if (result != null) {
            val event = ServiceToClientEvent.TransactionBuild(Instant.now(), req.id, result)
            val respMessage = net.createMessage(IN_EVENT_TOPIC, reqMessage.sessionID,
                    event.serialize().bits)
            net.send(respMessage, reqMessage.getReplyTo(services.networkMapCache))
        }
    }

    /**
     * Process a request from a monitor to remove them from the subscribers.
     */
    fun processDeregisterRequest(req: DeregisterRequest) {
        val message = try {
            // TODO: Session ID should be managed by the messaging layer, so it handles ensuring that the
            //       request comes from the same endpoint that registered at the start.
            listeners.remove(RegisteredListener(req.replyToRecipient, req.sessionID))
            net.createMessage(DEREGISTER_TOPIC, req.sessionID, DeregisterResponse(true).serialize().bits)
        } catch (ex: IllegalStateException) {
            net.createMessage(DEREGISTER_TOPIC, req.sessionID, DeregisterResponse(false).serialize().bits)
        }
        net.send(message, req.replyToRecipient)
    }

    /**
     * Process a request from a monitor to add them to the subscribers. This includes hooks to authenticate the request,
     * but currently all requests pass (and there's no access control on vaults, so it has no actual meaning).
     */
    fun processRegisterRequest(req: RegisterRequest) {
        try {
            listeners.add(RegisteredListener(req.replyToRecipient, req.sessionID))
            val stateMessage = StateSnapshotMessage(services.vaultService.currentVault.states.toList(),
                    smm.allStateMachines.map { it.javaClass.name })
            net.send(net.createMessage(STATE_TOPIC, DEFAULT_SESSION_ID, stateMessage.serialize().bits), req.replyToRecipient)

            val message = net.createMessage(REGISTER_TOPIC, req.sessionID, RegisterResponse(true).serialize().bits)
            net.send(message, req.replyToRecipient)
        } catch (ex: IllegalStateException) {
            val message = net.createMessage(REGISTER_TOPIC, req.sessionID, RegisterResponse(false).serialize().bits)
            net.send(message, req.replyToRecipient)
        }
    }

    private fun notifyEvent(event: ServiceToClientEvent) = listeners.forEach { monitor ->
        net.send(net.createMessage(IN_EVENT_TOPIC, monitor.sessionID, event.serialize().bits), monitor.recipients)
    }

    // TODO: Make a lightweight protocol that manages this workflow, rather than embedding it directly in the service
    private fun initiatePayment(req: ClientToServiceCommand.PayCash): TransactionBuildResult {
        val builder: TransactionBuilder = TransactionType.General.Builder(null)
        // TODO: Have some way of restricting this to states the caller controls
        try {
            Cash().generateSpend(builder, req.amount.withoutIssuer(), req.recipient.owningKey,
                    // TODO: Move cash state filtering by issuer down to the contract itself
                    services.vaultService.currentVault.statesOfType<Cash.State>().filter { it.state.data.amount.token == req.amount.token },
                    setOf(req.amount.token.issuer.party))
            .forEach {
                val key = services.keyManagementService.keys[it] ?: throw IllegalStateException("Could not find signing key for ${it.toStringShort()}")
                builder.signWith(KeyPair(it, key))
            }
            val tx = builder.toSignedTransaction(checkSufficientSignatures = false)
            val protocol = FinalityProtocol(tx, setOf(req), setOf(req.recipient))
            return TransactionBuildResult.ProtocolStarted(
                    smm.add(BroadcastTransactionProtocol.TOPIC, protocol).machineId,
                    tx.tx.toLedgerTransaction(services),
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
                    smm.add(BroadcastTransactionProtocol.TOPIC, protocol).machineId,
                    tx.tx.toLedgerTransaction(services),
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
                smm.add(BroadcastTransactionProtocol.TOPIC, protocol).machineId,
                tx.tx.toLedgerTransaction(services),
                "Cash issuance completed"
        )
    }

    class InputStateRefResolveFailed(stateRefs: List<StateRef>) :
            Exception("Failed to resolve input StateRefs $stateRefs")
}
