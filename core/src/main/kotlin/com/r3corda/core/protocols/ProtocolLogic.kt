package com.r3corda.core.protocols

import co.paralleluniverse.fibers.Suspendable
import com.r3corda.core.crypto.Party
import com.r3corda.core.messaging.Message
import com.r3corda.core.messaging.runOnNextMessage
import com.r3corda.core.node.ServiceHub
import com.r3corda.core.node.services.DEFAULT_SESSION_ID
import com.r3corda.core.serialization.deserialize
import com.r3corda.core.utilities.ProgressTracker
import com.r3corda.core.utilities.UntrustworthyData
import com.r3corda.core.utilities.debug
import com.r3corda.protocols.HandshakeMessage
import org.slf4j.Logger
import rx.Observable
import java.util.*
import java.util.concurrent.CompletableFuture

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

    /**
     * The topic to use when communicating with other parties. If more than one topic is required then use sub-protocols.
     * Note that this is temporary until protocol sessions are properly implemented.
     */
    protected abstract val topic: String

    private val sessions = HashMap<Party, Session>()

    /**
     * If a node receives a [HandshakeMessage] it needs to call this method on the initiated receipt protocol to enable
     * communication between it and the sender protocol. Calling this method, and other initiation steps, are already
     * handled by AbstractNodeService.addProtocolHandler.
     */
    fun registerSession(receivedHandshake: HandshakeMessage) {
        // Note that the send and receive session IDs are swapped
        addSession(receivedHandshake.replyToParty, receivedHandshake.receiveSessionID, receivedHandshake.sendSessionID)
    }

    // Kotlin helpers that allow the use of generic types.
    inline fun <reified T : Any> sendAndReceive(otherParty: Party, payload: Any): UntrustworthyData<T> {
        return sendAndReceive(otherParty, payload, T::class.java)
    }

    @Suspendable
    fun <T : Any> sendAndReceive(otherParty: Party, payload: Any, receiveType: Class<T>): UntrustworthyData<T> {
        val sendSessionId = getSendSessionId(otherParty, payload)
        val receiveSessionId = getReceiveSessionId(otherParty)
        return psm.sendAndReceive(topic, otherParty, sendSessionId, receiveSessionId, payload, receiveType)
    }

    inline fun <reified T : Any> receive(otherParty: Party): UntrustworthyData<T> = receive(otherParty, T::class.java)

    @Suspendable
    fun <T : Any> receive(otherParty: Party, receiveType: Class<T>): UntrustworthyData<T> {
        return psm.receive(topic, getReceiveSessionId(otherParty), receiveType)
    }

    @Suspendable
    fun send(otherParty: Party, payload: Any) {
        psm.send(topic, otherParty, getSendSessionId(otherParty, payload), payload)
    }

    private fun addSession(party: Party, sendSesssionId: Long, receiveSessionId: Long) {
        if (party in sessions) {
            logger.debug { "Existing session with party $party to be overwritten by new one" }
        }
        sessions[party] = Session(sendSesssionId, receiveSessionId)
    }

    private fun getSendSessionId(otherParty: Party, payload: Any): Long {
        return if (payload is HandshakeMessage) {
            addSession(otherParty, payload.sendSessionID, payload.receiveSessionID)
            DEFAULT_SESSION_ID
        } else {
            sessions[otherParty]?.sendSessionId ?:
                    throw IllegalStateException("Session with party $otherParty hasn't been established yet")
        }
    }

    private fun getReceiveSessionId(otherParty: Party): Long {
        return sessions[otherParty]?.receiveSessionId ?:
                throw IllegalStateException("Session with party $otherParty hasn't been established yet")
    }

    /**
     * Check if we already have a session with this party
     */
    protected fun hasSession(otherParty: Party) = sessions.containsKey(otherParty)

    /**
     * Invokes the given subprotocol by simply passing through this [ProtocolLogic]s reference to the
     * [ProtocolStateMachine] and then calling the [call] method.
     * @param inheritParentSessions In certain situations the subprotocol needs to inherit and use the same open
     * sessions of the parent. However in most cases this is not desirable as it prevents the subprotocol from
     * communicating with the same party on a different topic. For this reason the default value is false.
     */
    @JvmOverloads
    @Suspendable
    fun <R> subProtocol(subLogic: ProtocolLogic<R>, inheritParentSessions: Boolean = false): R {
        subLogic.psm = psm
        if (inheritParentSessions) {
            subLogic.sessions.putAll(sessions)
        }
        maybeWireUpProgressTracking(subLogic)
        val r = subLogic.call()
        // It's easy to forget this when writing protocols so we just step it to the DONE state when it completes.
        subLogic.progressTracker?.currentStep = ProgressTracker.DONE
        return r
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

    private data class Session(val sendSessionId: Long, val receiveSessionId: Long)

    // TODO this is not threadsafe, needs an atomic get-step-and-subscribe
    fun track(): Pair<String, Observable<String>>? {
        return progressTracker?.let {
            Pair(it.currentStep.toString(), it.changes.map { it.toString() })
        }
    }
}
