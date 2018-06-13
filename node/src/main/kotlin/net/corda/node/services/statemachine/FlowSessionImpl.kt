package net.corda.node.services.statemachine

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowInfo
import net.corda.core.flows.FlowSession
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.FlowIORequest
import net.corda.core.internal.FlowStateMachine
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NonEmptySet
import net.corda.core.utilities.UntrustworthyData
import net.corda.core.internal.checkPayloadIs
import net.corda.node.internal.exceptions.UnknownPeerException

class FlowSessionImpl(
        val partyName: CordaX500Name,
        val sourceSessionId: SessionId,
        var resolvedParty: Party? = null
) : FlowSession() {

    constructor(party: Party, sessionId: SessionId) : this(party.name, sessionId, party)

    override val counterparty: Party get() = resolveLazy()

//    private var resolvedParty: Party? = null

    private fun resolveLazy(): Party {
        if (resolvedParty == null) {
            resolvedParty = getFlowStateMachine().serviceHub.networkMapCache.getPeerByLegalName(partyName)

            if (resolvedParty == null) {
                throw UnknownPeerException("Could no resolve $partyName.")
            }
        }

        return resolvedParty!!
    }

    override fun toString() = "FlowSessionImpl(counterparty=$resolvedParty, sourceSessionId=$sourceSessionId)"

    override fun equals(other: Any?): Boolean {
        return (other as? FlowSessionImpl)?.sourceSessionId == sourceSessionId
    }

    override fun hashCode() = sourceSessionId.hashCode()

    private fun getFlowStateMachine(): FlowStateMachine<*> {
        return Fiber.currentFiber() as FlowStateMachine<*>
    }

    @Suspendable
    override fun getCounterpartyFlowInfo(maySkipCheckpoint: Boolean): FlowInfo {
        val request = FlowIORequest.GetFlowInfo(NonEmptySet.of(this))
        return getFlowStateMachine().suspend(request, maySkipCheckpoint)[this]!!
    }

    @Suspendable
    override fun getCounterpartyFlowInfo() = getCounterpartyFlowInfo(maySkipCheckpoint = false)

    @Suspendable
    override fun <R : Any> sendAndReceive(
            receiveType: Class<R>,
            payload: Any,
            maySkipCheckpoint: Boolean
    ): UntrustworthyData<R> {
        enforceNotPrimitive(receiveType)
        val request = FlowIORequest.SendAndReceive(
                sessionToMessage = mapOf(this to payload.serialize(context = SerializationDefaults.P2P_CONTEXT)),
                shouldRetrySend = false
        )
        val responseValues: Map<FlowSession, SerializedBytes<Any>> = getFlowStateMachine().suspend(request, maySkipCheckpoint)
        val responseForCurrentSession = responseValues[this]!!

        return responseForCurrentSession.checkPayloadIs(receiveType)
    }

    @Suspendable
    override fun <R : Any> sendAndReceive(receiveType: Class<R>, payload: Any) = sendAndReceive(receiveType, payload, maySkipCheckpoint = false)

    @Suspendable
    override fun <R : Any> receive(receiveType: Class<R>, maySkipCheckpoint: Boolean): UntrustworthyData<R> {
        enforceNotPrimitive(receiveType)
        val request = FlowIORequest.Receive(NonEmptySet.of(this))
        return getFlowStateMachine().suspend(request, maySkipCheckpoint)[this]!!.checkPayloadIs(receiveType)
    }

    @Suspendable
    override fun <R : Any> receive(receiveType: Class<R>) = receive(receiveType, maySkipCheckpoint = false)

    @Suspendable
    override fun send(payload: Any, maySkipCheckpoint: Boolean) {
        val request = FlowIORequest.Send(
                sessionToMessage = mapOf(this to payload.serialize(context = SerializationDefaults.P2P_CONTEXT))
        )
        return getFlowStateMachine().suspend(request, maySkipCheckpoint)
    }

    @Suspendable
    override fun send(payload: Any) = send(payload, maySkipCheckpoint = false)

    private fun enforceNotPrimitive(type: Class<*>) {
        require(!type.isPrimitive) { "Cannot receive primitive type $type" }
    }
}
