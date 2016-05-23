@file:Suppress("UNUSED_PARAMETER")

package com.r3corda.node.testutils

import com.r3corda.contracts.DummyContract
import com.r3corda.core.contracts.StateRef
import com.r3corda.core.crypto.Party
import com.r3corda.core.testing.DUMMY_NOTARY
import com.r3corda.core.testing.DUMMY_NOTARY_KEY
import com.r3corda.node.internal.AbstractNode
import java.util.*

fun issueState(node: AbstractNode, notary: Party = DUMMY_NOTARY): StateRef {
    val tx = DummyContract().generateInitial(node.info.identity.ref(0), Random().nextInt(), DUMMY_NOTARY)
    tx.signWith(node.storage.myLegalIdentityKey)
    tx.signWith(DUMMY_NOTARY_KEY)
    val stx = tx.toSignedTransaction()
    node.services.recordTransactions(listOf(stx))
    return StateRef(stx.id, 0)
}
