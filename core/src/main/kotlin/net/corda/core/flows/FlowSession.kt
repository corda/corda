package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.DoNotImplement
import net.corda.core.identity.Party
import net.corda.core.utilities.UntrustworthyData

/**
 *
 * A [FlowSession] is a handle on a communication sequence between two paired flows, possibly running on separate nodes.
 *   It is used to send and receive messages between the flows as well as to query information about the counter-flow.
 *
 * There are two ways of obtaining such a session:
 *
 * 1.  Calling [FlowLogic.initiateFlow]. This will create a [FlowSession] object on which the first send/receive
 *   operation will attempt to kick off a corresponding [InitiatedBy] flow on the counterparty's node.
 * 2.  As constructor parameter to [InitiatedBy] flows. This session is the one corresponding to the initiating flow and
 *   may be used for replies.
 *
 * To port flows using the old Party-based API:
 *
 * Look for [Deprecated] usages of send/receive/sendAndReceive/getFlowInfo.
 *
 * If it's an InitiatingFlow:
 *
 *   Look for the send/receive that kicks off the counter flow. Insert a
 *
 *     val session = initiateFlow(party)
 *
 *   and use this session afterwards for send/receives.
 *   For example:
 *     send(party, something)
 *   will become
 *     session.send(something)
 *
 * If it's an InitiatedBy flow:
 *
 *   Change the constructor to take an otherSideSession: FlowSession instead of a counterparty: Party
 *   Then look for usages of the deprecated functions and change them to use the FlowSession
 *   For example:
 *     send(counterparty, something)
 *   will become
 *     otherSideSession.send(something)
 */
@DoNotImplement
abstract class FlowSession {
    /**
     * The [Party] on the other side of this session. In the case of a session created by [FlowLogic.initiateFlow]
     *   [counterparty] is the same Party as the one passed to that function.
     */
    abstract val counterparty: Party

    /**
     * Returns a [FlowInfo] object describing the flow [counterparty] is using. With [FlowInfo.flowVersion] it
     * provides the necessary information needed for the evolution of flows and enabling backwards compatibility.
     *
     * This method can be called before any send or receive has been done with [counterparty]. In such a case this will
     * force them to start their flow.
     *
     * @param maySkipCheckpoint setting it to true indicates to the platform that it may optimise away the checkpoint.
     */
    @Suspendable
    abstract fun getCounterpartyFlowInfo(maySkipCheckpoint: Boolean): FlowInfo

    /**
     * Returns a [FlowInfo] object describing the flow [counterparty] is using. With [FlowInfo.flowVersion] it
     * provides the necessary information needed for the evolution of flows and enabling backwards compatibility.
     *
     * This method can be called before any send or receive has been done with [counterparty]. In such a case this will
     * force them to start their flow.
     */
    @Suspendable
    abstract fun getCounterpartyFlowInfo(): FlowInfo

    /**
     * Serializes and queues the given [payload] object for sending to the [counterparty]. Suspends until a response
     * is received, which must be of the given [R] type.
     *
     * Remember that when receiving data from other parties the data should not be trusted until it's been thoroughly
     * verified for consistency and that all expectations are satisfied, as a malicious peer may send you subtly
     * corrupted data in order to exploit your code.
     *
     * Note that this function is not just a simple send+receive pair: it is more efficient and more correct to
     * use this when you expect to do a message swap than do use [send] and then [receive] in turn.
     *
     * @return an [UntrustworthyData] wrapper around the received object.
     */
    @Suspendable
    inline fun <reified R : Any> sendAndReceive(payload: Any): UntrustworthyData<R> {
        return sendAndReceive(R::class.java, payload)
    }

    /**
     * Serializes and queues the given [payload] object for sending to the [counterparty]. Suspends until a response
     * is received, which must be of the given [receiveType]. Remember that when receiving data from other parties the
     * data should not be trusted until it's been thoroughly verified for consistency and that all expectations are
     * satisfied, as a malicious peer may send you subtly corrupted data in order to exploit your code.
     *
     * Note that this function is not just a simple send+receive pair: it is more efficient and more correct to
     * use this when you expect to do a message swap than do use [send] and then [receive] in turn.
     *
     * @param maySkipCheckpoint setting it to true indicates to the platform that it may optimise away the checkpoint.
     * @return an [UntrustworthyData] wrapper around the received object.
     */
    @Suspendable
    abstract fun <R : Any> sendAndReceive(
            receiveType: Class<R>,
            payload: Any, maySkipCheckpoint: Boolean
    ): UntrustworthyData<R>

    /**
     * Serializes and queues the given [payload] object for sending to the [counterparty]. Suspends until a response
     * is received, which must be of the given [receiveType]. Remember that when receiving data from other parties the
     * data should not be trusted until it's been thoroughly verified for consistency and that all expectations are
     * satisfied, as a malicious peer may send you subtly corrupted data in order to exploit your code.
     *
     * Note that this function is not just a simple send+receive pair: it is more efficient and more correct to
     * use this when you expect to do a message swap than do use [send] and then [receive] in turn.
     *
     * @return an [UntrustworthyData] wrapper around the received object.
     */
    @Suspendable
    abstract fun <R : Any> sendAndReceive(receiveType: Class<R>, payload: Any): UntrustworthyData<R>

    /**
     * Suspends until [counterparty] sends us a message of type [R].
     *
     * Remember that when receiving data from other parties the data should not be trusted until it's been thoroughly
     * verified for consistency and that all expectations are satisfied, as a malicious peer may send you subtly
     * corrupted data in order to exploit your code.
     */
    @Suspendable
    inline fun <reified R : Any> receive(): UntrustworthyData<R> {
        return receive(R::class.java)
    }

    /**
     * Suspends until [counterparty] sends us a message of type [receiveType].
     *
     * Remember that when receiving data from other parties the data should not be trusted until it's been thoroughly
     * verified for consistency and that all expectations are satisfied, as a malicious peer may send you subtly
     * corrupted data in order to exploit your code.
     *
     * @param maySkipCheckpoint setting it to true indicates to the platform that it may optimise away the checkpoint.
     * @return an [UntrustworthyData] wrapper around the received object.
     */
    @Suspendable
    abstract fun <R : Any> receive(receiveType: Class<R>, maySkipCheckpoint: Boolean): UntrustworthyData<R>

    /**
     * Suspends until [counterparty] sends us a message of type [receiveType].
     *
     * Remember that when receiving data from other parties the data should not be trusted until it's been thoroughly
     * verified for consistency and that all expectations are satisfied, as a malicious peer may send you subtly
     * corrupted data in order to exploit your code.
     *
     * @return an [UntrustworthyData] wrapper around the received object.
     */
    @Suspendable
    abstract fun <R : Any> receive(receiveType: Class<R>): UntrustworthyData<R>

    /**
     * Queues the given [payload] for sending to the [counterparty] and continues without suspending.
     *
     * Note that the other party may receive the message at some arbitrary later point or not at all: if [counterparty]
     * is offline then message delivery will be retried until it comes back or until the message is older than the
     * network's event horizon time.
     *
     * @param maySkipCheckpoint setting it to true indicates to the platform that it may optimise away the checkpoint.
     */
    @Suspendable
    abstract fun send(payload: Any, maySkipCheckpoint: Boolean)

    /**
     * Queues the given [payload] for sending to the [counterparty] and continues without suspending.
     *
     * Note that the other party may receive the message at some arbitrary later point or not at all: if [counterparty]
     * is offline then message delivery will be retried until it comes back or until the message is older than the
     * network's event horizon time.
     */
    @Suspendable
    abstract fun send(payload: Any)
}
