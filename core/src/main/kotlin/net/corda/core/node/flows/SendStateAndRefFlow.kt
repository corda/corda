package net.corda.core.node.flows

import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party

/**
 * The [SendStateAndRefFlow] should be used to send a list of input [StateAndRef] to another peer that wishes to verify
 * the input's integrity by resolving and checking the dependencies as well. The other side should invoke [ReceiveStateAndRefFlow]
 * at the right point in the conversation to receive the input state and ref and perform the resolution back-and-forth
 * required to check the dependencies.
 *
 * @param otherSide the target party.
 * @param stateAndRefs the list of [StateAndRef] being sent to the [otherSide].
 */
open class SendStateAndRefFlow(otherSide: Party, stateAndRefs: List<StateAndRef<*>>) : DataVendingFlow(otherSide, stateAndRefs)