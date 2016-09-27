package com.r3corda.core.protocols

import co.paralleluniverse.fibers.Suspendable
import com.r3corda.core.crypto.Party
import com.r3corda.core.node.ServiceHub
import com.r3corda.core.utilities.ProgressTracker
import com.r3corda.core.utilities.UntrustworthyData
import org.slf4j.Logger
import rx.Observable

/**
 * A sub-class of [ProtocolLogic<T>] implements a protocol flow using direct, straight line blocking code. Thus you
 * can write complex protocol logic in an ordinary fashion, without having to think about callbacks, restarting after
 * a node crash, how many instances of your protocol there are running and so on.
 *
 * Invoking the network will cause the call stack to be suspended onto the heap and then serialized to a database using
 * the Quasar fibers framework. Because of this, if you need access to data that might change over time, you should
 * request it just-in-time via the [serviceHub] property which is provided. Don't try and keep data you got from a
 * service across calls to send/receive/sendAndReceive because the world might change in arbitrary ways out from
 * underneath you, for instance, if the node is restarted or reconfigured!
 *
 * Additionally, be aware of what data you pin either via the stack or in your [ProtocolLogic] implementation. Very large
 * objects or datasets will hurt performance by increasing the amount of data stored in each checkpoint.
 *
 * If you'd like to use another ProtocolLogic class as a component of your own, construct it on the fly and then pass
 * it to the [subProtocol] method. It will return the result of that protocol when it completes.
 */
abstract class ProtocolLogic<out T> {

    /** Reference to the [Fiber] instance that is the top level controller for the entire flow. */
    lateinit var psm: ProtocolStateMachine<*>

    /** This is where you should log things to. */
    val logger: Logger get() = psm.logger

    /**
     * Provides access to big, heavy classes that may be reconstructed from time to time, e.g. across restarts. It is
     * only available once the protocol has started, which means it cannnot be accessed in the constructor. Either
     * access this lazily or from inside [call].
     */
    val serviceHub: ServiceHub get() = psm.serviceHub

    private var sessionProtocol: ProtocolLogic<*> = this

    /**
     * Return the marker [Class] which [party] has used to register the counterparty protocol that is to execute on the
     * other side. The default implementation returns the class object of this ProtocolLogic, but any [Class] instance
     * will do as long as the other side registers with it.
     */
    open fun getCounterpartyMarker(party: Party): Class<*> = javaClass

    // Kotlin helpers that allow the use of generic types.
    inline fun <reified T : Any> sendAndReceive(otherParty: Party, payload: Any): UntrustworthyData<T> {
        return sendAndReceive(otherParty, payload, T::class.java)
    }

    @Suspendable
    fun <T : Any> sendAndReceive(otherParty: Party, payload: Any, receiveType: Class<T>): UntrustworthyData<T> {
        return psm.sendAndReceive(otherParty, payload, receiveType, sessionProtocol)
    }

    inline fun <reified T : Any> receive(otherParty: Party): UntrustworthyData<T> = receive(otherParty, T::class.java)

    @Suspendable
    fun <T : Any> receive(otherParty: Party, receiveType: Class<T>): UntrustworthyData<T> {
        return psm.receive(otherParty, receiveType, sessionProtocol)
    }

    @Suspendable
    fun send(otherParty: Party, payload: Any) {
        psm.send(otherParty, payload, sessionProtocol)
    }

    /**
     * Invokes the given subprotocol by simply passing through this [ProtocolLogic]s reference to the
     * [ProtocolStateMachine] and then calling the [call] method.
     * @param shareParentSessions In certain situations the need arises to use the same sessions the parent protocol has
     * already established. However this also prevents the subprotocol from creating new sessions with those parties.
     * For this reason the default value is false.
     */
    // TODO Rethink the default value for shareParentSessions
    // TODO shareParentSessions is a bit too low-level and perhaps can be expresed in a better way
    @Suspendable
    fun <R> subProtocol(subLogic: ProtocolLogic<R>, shareParentSessions: Boolean = false): R {
        subLogic.psm = psm
        maybeWireUpProgressTracking(subLogic)
        if (shareParentSessions) {
            subLogic.sessionProtocol = this
        }
        val result = subLogic.call()
        // It's easy to forget this when writing protocols so we just step it to the DONE state when it completes.
        subLogic.progressTracker?.currentStep = ProgressTracker.DONE
        return result
    }

    private fun maybeWireUpProgressTracking(subLogic: ProtocolLogic<*>) {
        val ours = progressTracker

        val theirs = subLogic.progressTracker
        if (ours != null && theirs != null) {
            if (ours.currentStep == ProgressTracker.UNSTARTED) {
                logger.warn("ProgressTracker has not been started for $this")
                ours.nextStep()
            }
            ours.setChildProgressTracker(ours.currentStep, theirs)
        }
    }

    /**
     * Override this to provide a [ProgressTracker]. If one is provided and stepped, the framework will do something
     * helpful with the progress reports. If this protocol is invoked as a sub-protocol of another, then the
     * tracker will be made a child of the current step in the parent. If it's null, this protocol doesn't track
     * progress.
     *
     * Note that this has to return a tracker before the protocol is invoked. You can't change your mind half way
     * through.
     */
    open val progressTracker: ProgressTracker? = null

    /** This is where you fill out your business logic. */
    @Suspendable
    abstract fun call(): T

    // TODO this is not threadsafe, needs an atomic get-step-and-subscribe
    fun track(): Pair<String, Observable<String>>? {
        return progressTracker?.let {
            Pair(it.currentStep.toString(), it.changes.map { it.toString() })
        }
    }

}
