package com.r3.corda.enterprise.perftestcordapp.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.enterprise.perftestcordapp.flows.AbstractCashFlow.Companion.GENERATING_ID
import net.corda.confidential.SwapIdentitiesFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.utilities.ProgressTracker

/**
 * The basis for writing an efficient (from a messaging perspective) initiating flow that optionally allows use of confidential identities,
 * and is based on [AbstractCashFlow].
 *
 * The general concept is that the flow will "fork" itself into a form that uses confidential identities if requested.
 *
 * Implement the abstract method [mainCall] to implement all your flow logic.  Also implement [makeAnonymousFlow]
 * to create a "forked" instance of the flow.
 *
 * @param anonymous True if you want to use use confidential identities.
 * @param recipient The non-confidential identity with whom to initiate.
 */
abstract class AbstractConfidentialAwareCashFlow<T>(val anonymous: Boolean,
                                                    val recipient: Party,
                                                    progressTracker: ProgressTracker) : AbstractCashFlow<AbstractCashFlow.Result>(progressTracker) {

    /**
     * Post session initiation processing to be implemented by subclasses.  By the time this is called, confidential identities would have been swapped.
     *
     * @param maybeAnonymousRecipient will either be a [Party] or [AnonymousParty], depending on whether confidential identities are being used.
     * @param recipientSession a [FlowSession] to communicate with the initated flow.  Confidential identity swapping will already have been carried out.
     */
    @Suspendable
    abstract protected fun mainCall(maybeAnonymousRecipient: AbstractParty, recipientSession: FlowSession): Result

    /**
     * Make a clone of this flow that has [anonymous] == true (uses confidential identities) that initiates a subclass of
     * [AbstractConfidentialAwareCashResponderFlow] that has [anonymous] == true.
     */
    abstract protected fun makeAnonymousFlow(): AbstractConfidentialAwareCashFlow<T>

    private var forked = false

    @Suspendable
    override fun call(): Result = if (!forked && anonymous) {
        val forkedFlow = makeAnonymousFlow()
        forkedFlow.forked = true
        subFlow(forkedFlow)
    } else {
        val recipientSession = initiateFlow(recipient)
        val anonymousRecipient: AbstractParty = if (anonymous) {
            progressTracker.currentStep = GENERATING_ID
            subFlow(SwapIdentitiesFlow(recipientSession))[recipient]!!
        } else recipient
        mainCall(anonymousRecipient, recipientSession)
    }
}

/**
 * The basis for writing efficient (from a messaging perspective) initiated flow that optionally allows use of confidential identities.
 * Subclasses are expected to be initiated by subclasses of [AbstractConfidentialAwareCashFlow].
 *
 * @param anonymous Should be set approriately by the subclass dependent on whether it has been initiated by a [AbstractConfidentialAwareCashFlow]
 * subclass that is using confidential identities.  i.e. the value of this field should match that of the initating subclass.
 */
abstract class AbstractConfidentialAwareCashResponderFlow<T>(protected val anonymous: Boolean, protected val otherSide: FlowSession) : FlowLogic<T>() {
    @Suspendable
    override fun call(): T {
        if (anonymous) subFlow(SwapIdentitiesFlow(otherSide))
        return respond()
    }

    @Suspendable
    abstract protected fun respond(): T
}