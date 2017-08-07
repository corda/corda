package net.corda.node.services.transactions

import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.parsePublicKeyBase58
import net.corda.core.identity.Party
import net.corda.core.internal.ThreadBox
import net.corda.core.node.services.UniquenessException
import net.corda.core.node.services.UniquenessProvider
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.loggerFor
import net.corda.node.utilities.*
import org.bouncycastle.asn1.x500.X500Name
import java.util.*
import javax.annotation.concurrent.ThreadSafe
import javax.persistence.*

/** A RDBMS backed Uniqueness provider */
@ThreadSafe
class PersistentUniquenessProvider : UniquenessProvider, SingletonSerializeAsToken() {

   @Entity
   @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}notary_commit_log")
   class PersistentUniqueness (

           @EmbeddedId
           var id: StateRef = StateRef(),

           @Column(name = "consuming_transaction_id")
           var consumingTxHash: String = "",

           @Column(name = "consuming_input_index", length = 36)
           var consumingIndex: Int = 0,

           @Embedded
           var party: Party = Party()
   ) {

       @Embeddable
       data class StateRef (
               @Column(name = "transaction_id")
               var txId: String = "",

               @Column(name = "output_index", length = 36)
               var index: Int = 0
       ) : java.io.Serializable

       @Embeddable
       data class Party  (
               @Column(name = "requesting_party_name")
               var name: String = "",

               @Column(name = "requesting_party_key", length = 255)
               var owningKey: String = ""
       ) : java.io.Serializable
   }

    private class InnerState {
        val committedStates = createMap()
    }

    private val mutex = ThreadBox(InnerState())

    companion object {
        private val log = loggerFor<PersistentUniquenessProvider>()

        fun createMap(): AppendOnlyPersistentMap<StateRef, UniquenessProvider.ConsumingTx, PersistentUniqueness, PersistentUniqueness.StateRef> {
            return AppendOnlyPersistentMap(
                    toPersistentEntityKey = { PersistentUniqueness.StateRef(it.txhash.toString(), it.index) },
                    fromPersistentEntity = {
                        Pair(StateRef(SecureHash.parse(it.id.txId), it.id.index),
                                UniquenessProvider.ConsumingTx(SecureHash.parse(it.consumingTxHash), it.consumingIndex,
                                        Party(X500Name(it.party.name), parsePublicKeyBase58(it.party.owningKey))))
                    },
                    toPersistentEntity = { key: StateRef, value: UniquenessProvider.ConsumingTx ->
                        PersistentUniqueness().apply {
                            id = PersistentUniqueness.StateRef(key.txhash.toString(), key.index)
                            consumingTxHash = value.id.toString()
                            consumingIndex = value.inputIndex
                            party = PersistentUniqueness.Party(value.requestingParty.name.toString())
                        }
                    },
                    persistentEntityClass = PersistentUniqueness::class.java
            )
        }
    }

    override fun commit(states: List<StateRef>, txId: SecureHash, callerIdentity: Party) {

        val conflict = mutex.locked {
                    val conflictingStates = LinkedHashMap<StateRef, UniquenessProvider.ConsumingTx>()
                    for (inputState in states) {
                        val consumingTx = committedStates.get(inputState)
                        if (consumingTx != null) conflictingStates[inputState] = consumingTx
                    }
                    if (conflictingStates.isNotEmpty()) {
                        log.debug("Failure, input states already committed: ${conflictingStates.keys}")
                        UniquenessProvider.Conflict(conflictingStates)
                    } else {
                        states.forEachIndexed { i, stateRef ->
                            committedStates[stateRef] = UniquenessProvider.ConsumingTx(txId, i, callerIdentity)
                        }
                        log.debug("Successfully committed all input states: $states")
                        null
                    }
                }

        if (conflict != null) throw UniquenessException(conflict)
    }
}
