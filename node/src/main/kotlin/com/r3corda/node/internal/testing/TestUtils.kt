package com.r3corda.node.internal.testing

import com.r3corda.core.contracts.StateAndRef
import com.r3corda.core.contracts.DummyContract
import com.r3corda.core.contracts.StateRef
import com.r3corda.core.contracts.TransactionState
import com.r3corda.core.contracts.TransactionType
import com.r3corda.core.crypto.Party
import com.r3corda.core.seconds
import com.r3corda.core.testing.DUMMY_NOTARY
import com.r3corda.core.testing.DUMMY_NOTARY_KEY
import com.r3corda.node.internal.AbstractNode
import java.time.Instant
import java.util.*

fun issueState(node: AbstractNode): StateAndRef<*> {
    val tx = DummyContract().generateInitial(node.info.identity.ref(0), Random().nextInt(), DUMMY_NOTARY)
    tx.signWith(node.storage.myLegalIdentityKey)
    tx.signWith(DUMMY_NOTARY_KEY)
    val stx = tx.toSignedTransaction()
    node.services.recordTransactions(listOf(stx))
    return StateAndRef(tx.outputStates().first(), StateRef(stx.id, 0))
}

fun issueMultiPartyState(nodeA: AbstractNode, nodeB: AbstractNode): StateAndRef<DummyContract.MultiOwnerState> {
    val state = TransactionState(DummyContract.MultiOwnerState(0,
            listOf(nodeA.info.identity.owningKey, nodeB.info.identity.owningKey)), DUMMY_NOTARY)
    val tx = TransactionType.NotaryChange.Builder().withItems(state)
    tx.signWith(nodeA.storage.myLegalIdentityKey)
    tx.signWith(nodeB.storage.myLegalIdentityKey)
    tx.signWith(DUMMY_NOTARY_KEY)
    val stx = tx.toSignedTransaction()
    nodeA.services.recordTransactions(listOf(stx))
    nodeB.services.recordTransactions(listOf(stx))
    val stateAndRef = StateAndRef(state, StateRef(stx.id, 0))
    return stateAndRef
}

fun issueInvalidState(node: AbstractNode, notary: Party = DUMMY_NOTARY): StateAndRef<*> {
    val tx = DummyContract().generateInitial(node.info.identity.ref(0), Random().nextInt(), notary)
    tx.setTime(Instant.now(), notary, 30.seconds)
    tx.signWith(node.storage.myLegalIdentityKey)
    val stx = tx.toSignedTransaction(false)
    node.services.recordTransactions(listOf(stx))
    return StateAndRef(tx.outputStates().first(), StateRef(stx.id, 0))
}
