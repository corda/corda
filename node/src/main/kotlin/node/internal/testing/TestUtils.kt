@file:Suppress("UNUSED_PARAMETER")

package node.testutils

import contracts.DummyContract
import core.contracts.StateRef
import core.crypto.Party
import core.testing.DUMMY_NOTARY
import core.testing.DUMMY_NOTARY_KEY
import node.internal.AbstractNode
import java.util.*

fun issueState(node: AbstractNode, notary: Party = DUMMY_NOTARY): StateRef {
    val tx = DummyContract().generateInitial(node.info.identity.ref(0), Random().nextInt(), DUMMY_NOTARY)
    tx.signWith(node.storage.myLegalIdentityKey)
    tx.signWith(DUMMY_NOTARY_KEY)
    val stx = tx.toSignedTransaction()
    node.services.recordTransactions(listOf(stx))
    return StateRef(stx.id, 0)
}
