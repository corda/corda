package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.UntrustworthyData
import net.corda.core.utilities.debug
import org.slf4j.Logger
import rx.Observable

/**
 * A sub-class of [FlowLogic<T>] implements a flow using direct, straight line blocking code. Thus you
 * can write complex flow logic in an ordinary fashion, without having to think about callbacks, restarting after
 * a node crash, how many instances of your flow there are running and so on.
 *
 * Invoking the network will cause the call stack to be suspended onto the heap and then serialized to a database using
 * the Quasar fibers framework. Because of this, if you need access to data that might change over time, you should
 * request it just-in-time via the [serviceHub] property which is provided. Don't try and keep data you got from a
 * service across calls to send/receive/sendAndReceive because the world might change in arbitrary ways out from
 * underneath you, for instance, if the node is restarted or reconfigured!
 *
 * Additionally, be aware of what data you pin either via the stack or in your [FlowLogic] implementation. Very large
 * objects or datasets will hurt performance by increasing the amount of data stored in each checkpoint.
 *
 * If you'd like to use another FlowLogic class as a component of your own, construct it on the fly and then pass
 * it to the [subFlow] method. It will return the result of that flow when it completes.
 */
abstract class FlowLogic<out T> {
    /** This is where you should log things to. */
    val logger: Logger get() = stateMachine.logger

    /** Returns a wrapped [UUID] object that identifies this state machine run (i.e. subflows have the same identifier as their parents). */
    val runId: StateMachineRunId get() = stateMachine.id

    /**
     * Provides access to big, heavy classes that may be reconstructed from time to time, e.g. across restarts. It is
     * only available once the flow has started, which means it cannnot be accessed in the constructor. Either
     * access this lazily or from inside [call].
     */
    val serviceHub: ServiceHub get() = stateMachine.serviceHub

    @Deprecated("This is no longer used and will be removed in a future release. If you are using this to communicate " +
            "with the same party but for two different message streams, then the correct way of doing that is to use sub-flows",
            level = DeprecationLevel.ERROR)
    open fun getCounterpartyMarker(party: Party): Class<*> = javaClass

    /**
     * Serializes and queues the given [payload] object for sending to the [otherParty]. Suspends until a response
     * is received, which must be of the given [R] type.
     *
     * Remember that when receiving data from other parties the data should not be trusted until it's been thoroughly
     * verified for consistency and that all expectations are satisfied, as a malicious peer may send you subtly
     * corrupted data in order to exploit your code.
     *
     * Note that this function is not just a simple send+receive pair: it is more efficient and more correct to
     * use this when you expect to do a message swap than do use [send] and then [receive] in turn.
     *
     * @returns an [UntrustworthyData] wrapper around the received object.
     */
    inline fun <reified R : Any> sendAndReceive(otherParty: Party, payload: Any) = sendAndReceive(R::class.java, otherParty, payload)

    /**
     * Serializes and queues the given [payload] object for sending to the [otherParty]. Suspends until a response
     * is received, which must be of the given [receiveType]. Remember that when receiving data from other parties the data
     * should not be trusted until it's been thoroughly verified for consistency and that all expectations are
     * satisfied, as a malicious peer may send you subtly corrupted data in order to exploit your code.
     *
     * Note that this function is not just a simple send+receive pair: it is more efficient and more correct to
     * use this when you expect to do a message swap than do use [send] and then [receive] in turn.
     *
     * @returns an [UntrustworthyData] wrapper around the received object.
     */
    @Suspendable
    open fun <R : Any> sendAndReceive(receiveType: Class<R>, otherParty: Party, payload: Any): UntrustworthyData<R> {
        return stateMachine.sendAndReceive(receiveType, otherParty, payload, sessionFlow)
    }

    /** @see sendAndReceiveWithRetry */
    internal inline fun <reified R : Any> sendAndReceiveWithRetry(otherParty: Party, payload: Any) = sendAndReceiveWithRetry(R::class.java, otherParty, payload)

    /**
     * Similar to [sendAndReceive] but also instructs the `payload` to be redelivered until the expected message is received.
     *
     * Note that this method should NOT be used for regular party-to-party communication, use [sendAndReceive] instead.
     * It is only intended for the case where the [otherParty] is running a distributed service with an idempotent
     * flow which only accepts a single request and sends back a single response – e.g. a notary or certain types of
     * oracle services. If one or more nodes in the service cluster go down mid-session, the message will be redelivered
     * to a different one, so there is no need to wait until the initial node comes back up to obtain a response.
     */
    @Suspendable
    internal open fun <R : Any> sendAndReceiveWithRetry(receiveType: Class<R>, otherParty: Party, payload: Any): UntrustworthyData<R> {
        return stateMachine.sendAndReceive(receiveType, otherParty, payload, sessionFlow, true)
    }

    /**
     * Suspends until the specified [otherParty] sends us a message of type [R].
     *
     * Remember that when receiving data from other parties the data should not be trusted until it's been thoroughly
     * verified for consistency and that all expectations are satisfied, as a malicious peer may send you subtly
     * corrupted data in order to exploit your code.
     */
    inline fun <reified R : Any> receive(otherParty: Party): UntrustworthyData<R> = receive(R::class.java, otherParty)

    /**
     * Suspends until the specified [otherParty] sends us a message of type [receiveType].
     *
     * Remember that when receiving data from other parties the data should not be trusted until it's been thoroughly
     * verified for consistency and that all expectations are satisfied, as a malicious peer may send you subtly
     * corrupted data in order to exploit your code.
     *
     * @returns an [UntrustworthyData] wrapper around the received object.
     */
    @Suspendable
    open fun <R : Any> receive(receiveType: Class<R>, otherParty: Party): UntrustworthyData<R> {
        return stateMachine.receive(receiveType, otherParty, sessionFlow)
    }

    /**
     * Queues the given [payload] for sending to the [otherParty] and continues without suspending.
     *
     * Note that the other party may receive the message at some arbitrary later point or not at all: if [otherParty]
     * is offline then message delivery will be retried until it comes back or until the message is older than the
     * network's event horizon time.
     */
    @Suspendable
    open fun send(otherParty: Party, payload: Any) = stateMachine.send(otherParty, payload, sessionFlow)

    /**
     * Invokes the given subflow. This function returns once the subflow completes successfully with the result
     * returned by that subflows [call] method. If the subflow has a progress tracker, it is attached to the
     * current step in this flow's progress tracker.
     *
     * @param shareParentSessions In certain situations the need arises to use the same sessions the parent flow has
     * already established. However this also prevents the subflow from creating new sessions with those parties.
     * For this reason the default value is false.
     *
     * @throws FlowException This is either thrown by [subLogic] itself or propagated from any of the remote
     * [FlowLogic]s it communicated with. A subflow retry can be done by catching this exception.
     */
    // TODO Rethink the default value for shareParentSessions
    // TODO shareParentSessions is a bit too low-level and perhaps can be expresed in a better way
    @Suspendable
    @JvmOverloads
    @Throws(FlowException::class)
    open fun <R> subFlow(subLogic: FlowLogic<R>, shareParentSessions: Boolean = false): R {
        subLogic.stateMachine = stateMachine
        maybeWireUpProgressTracking(subLogic)
        if (shareParentSessions) {
            subLogic.sessionFlow = this
        }
        logger.debug { "Calling subflow: $subLogic" }
        val result = subLogic.call()
        logger.debug { "Subflow finished with result $result" }
        // It's easy to forget this when writing flows so we just step it to the DONE state when it completes.
        subLogic.progressTracker?.currentStep = ProgressTracker.DONE
        return result
    }

    /**
     * Override this to provide a [ProgressTracker]. If one is provided and stepped, the framework will do something
     * helpful with the progress reports. If this flow is invoked as a subflow of another, then the
     * tracker will be made a child of the current step in the parent. If it's null, this flow doesn't track
     * progress.
     *
     * Note that this has to return a tracker before the flow is invoked. You can't change your mind half way
     * through.
     */
    open val progressTracker: ProgressTracker? = null

    /**
     * This is where you fill out your business logic. The returned object will usually be ignored, but can be
     * helpful if this flow is meant to be used as a subflow.
     */
    @Suspendable
    @Throws(FlowException::class)
    abstract fun call(): T

    /**
     * Returns a pair of the current progress step, as a string, and an observable of stringified changes to the
     * [progressTracker].
     *
     * @return Returns null if this flow has no progress tracker.
     */
    fun track(): Pair<String, Observable<String>>? {
        // TODO this is not threadsafe, needs an atomic get-step-and-subscribe
        return progressTracker?.let {
            Pair(it.currentStep.toString(), it.changes.map { it.toString() })
        }
    }

    /**
     * Suspends the flow until the transaction with the specified ID is received, successfully verified and
     * sent to the vault for processing. Note that this call suspends until the transaction is considered
     * valid by the local node, but that doesn't imply the vault will consider it relevant.
     */
    @Suspendable
    fun waitForLedgerCommit(hash: SecureHash): SignedTransaction {
        return stateMachine.waitForLedgerCommit(hash, this)
    }

   ////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private var _stateMachine: FlowStateMachine<*>? = null
    /**
     * @suppress
     * Internal only. Reference to the [co.paralleluniverse.fibers.Fiber] instance that is the top level controller for
     * the entire flow. When inside a flow this is equivalent to [co.paralleluniverse.strands.Strand.currentStrand]. This
     * is public only because it must be accessed across module boundaries.
     */
    var stateMachine: FlowStateMachine<*>
        get() = _stateMachine ?: throw IllegalStateException("This can only be done after the flow has been started.")
        set(value) {
            _stateMachine = value
        }

    // This points to the outermost flow and is changed when a subflow is invoked.
    private var sessionFlow: FlowLogic<*> = this

    private fun maybeWireUpProgressTracking(subLogic: FlowLogic<*>) {
        val ours = progressTracker
        val theirs = subLogic.progressTracker
        if (ours != null && theirs != null) {
            if (ours.currentStep == ProgressTracker.UNSTARTED) {
                logger.warn("ProgressTracker has not been started")
                ours.nextStep()
            }
            ours.setChildProgressTracker(ours.currentStep, theirs)
        }
    }
}
