package net.corda.verifier

import net.corda.client.mock.*
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.crypto.sha256
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.testing.contracts.DummyContract
import net.corda.testing.getTestX509Name
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.PublicKey
import java.util.*

/**
 * [GeneratedLedger] is a ledger with transactions that always verify.
 * It provides generator methods, in particular [transactionGenerator] that generates a valid transaction and also
 * returns the new state of the ledger.
 */
data class GeneratedLedger(
        val transactions: List<WireTransaction>,
        // notary -> outputs. We need to track this because of the unique-notary-on-inputs invariant
        val availableOutputs: Map<Party, List<StateAndRef<ContractState>>>,
        val attachments: Set<Attachment>,
        val identities: Set<Party>
) {
    val hashTransactionMap: Map<SecureHash, WireTransaction> by lazy { transactions.associateBy(WireTransaction::id) }
    val attachmentMap: Map<SecureHash, Attachment> by lazy { attachments.associateBy(Attachment::id) }
    val identityMap: Map<PublicKey, Party> by lazy { identities.associateBy(Party::owningKey) }

    companion object {
        val empty = GeneratedLedger(emptyList(), emptyMap(), emptySet(), emptySet())
    }

    fun resolveWireTransaction(transaction: WireTransaction): LedgerTransaction {
        return transaction.toLedgerTransaction(
                resolveIdentity = { identityMap[it] },
                resolveAttachment = { attachmentMap[it] },
                resolveStateRef = { hashTransactionMap[it.txhash]?.outputs?.get(it.index) }
        )
    }

    val attachmentsGenerator: Generator<List<Attachment>> by lazy {
        Generator.replicatePoisson(1.0, pickOneOrMaybeNew(attachments, attachmentGenerator))
    }

    val commandsGenerator: Generator<List<Pair<Command<*>, Party>>> by lazy {
        Generator.replicatePoisson(4.0, commandGenerator(identities))
    }

    /**
     * Generates an issuance(root) transaction.
     * Invariants: The input list must be empty.
     */
    val issuanceGenerator: Generator<Pair<WireTransaction, GeneratedLedger>> by lazy {
        val outputsGen = outputsGenerator.flatMap { outputs ->
            Generator.sequence(
                    outputs.map { output ->
                        pickOneOrMaybeNew(identities, partyGenerator).map { notary ->
                            TransactionState(output, notary, null)
                        }
                    }
            )
        }
        attachmentsGenerator.combine(outputsGen, commandsGenerator) { txAttachments, outputs, commands ->
            val newTransaction = WireTransaction(
                    emptyList(),
                    txAttachments.map { it.id },
                    outputs,
                    commands.map { it.first },
                    null,
                    null
            )
            val newOutputStateAndRefs = outputs.mapIndexed { i, state ->
                StateAndRef(state, StateRef(newTransaction.id, i))
            }
            val newAvailableOutputs = availableOutputs + newOutputStateAndRefs.groupBy { it.state.notary }
            val newAttachments = attachments + txAttachments
            val newIdentities = identities + commands.map { it.second } + outputs.map { it.notary }
            val newLedger = GeneratedLedger(transactions + newTransaction, newAvailableOutputs, newAttachments, newIdentities)
            Pair(newTransaction, newLedger)
        }
    }

    /**
     * Generates a regular non-issue transaction.
     * Invariants:
     *   * Input and output notaries must be one and the same.
     */
    fun regularTransactionGenerator(inputNotary: Party, inputsToChooseFrom: List<StateAndRef<ContractState>>): Generator<Pair<WireTransaction, GeneratedLedger>> {
        val outputsGen = outputsGenerator.map { outputs ->
            outputs.map { output ->
                TransactionState(output, inputNotary, null)
            }
        }
        val inputsGen = Generator.sampleBernoulli(inputsToChooseFrom)
        return inputsGen.combine(attachmentsGenerator, outputsGen, commandsGenerator) { inputs, txAttachments, outputs, commands ->
            val newTransaction = WireTransaction(
                    inputs.map { it.ref },
                    txAttachments.map { it.id },
                    outputs,
                    commands.map { it.first },
                    inputNotary,
                    null
            )
            val newOutputStateAndRefs = outputs.mapIndexed { i, state ->
                StateAndRef(state, StateRef(newTransaction.id, i))
            }
            val availableOutputsMinusConsumed = HashMap(availableOutputs)
            if (inputs.size == inputsToChooseFrom.size) {
                availableOutputsMinusConsumed.remove(inputNotary)
            } else {
                availableOutputsMinusConsumed[inputNotary] = inputsToChooseFrom - inputs
            }
            val newAvailableOutputs = availableOutputsMinusConsumed + newOutputStateAndRefs.groupBy { it.state.notary }
            val newAttachments = attachments + txAttachments
            val newIdentities = identities + commands.map { it.second }
            val newLedger = GeneratedLedger(transactions + newTransaction, newAvailableOutputs, newAttachments, newIdentities)
            Pair(newTransaction, newLedger)
        }
    }

    /**
     * Generates a valid transaction. It may be either an issuance or a regular spend transaction. These have
     * different invariants on notary fields.
     */
    val transactionGenerator: Generator<Pair<WireTransaction, GeneratedLedger>> by lazy {
        if (availableOutputs.isEmpty()) {
            issuanceGenerator
        } else {
            Generator.pickOne(availableOutputs.keys.toList()).flatMap { inputNotary ->
                val inputsToChooseFrom = availableOutputs[inputNotary]!!
                Generator.frequency(
                        0.5 to issuanceGenerator,
                        0.5 to regularTransactionGenerator(inputNotary, inputsToChooseFrom)
                )
            }
        }
    }
}

data class GeneratedState(
        val nonce: Long,
        override val participants: List<AbstractParty>
) : ContractState {
    override val contract = DummyContract()
}

class GeneratedAttachment(
        val bytes: ByteArray
) : Attachment {
    override val id = bytes.sha256()
    override fun open() = ByteArrayInputStream(bytes)
}

class GeneratedCommandData(
        val nonce: Long
) : CommandData

val keyPairGenerator = Generator.long().map { entropyToKeyPair(BigInteger.valueOf(it)) }
val publicKeyGenerator = keyPairGenerator.map { it.public }
val stateGenerator: Generator<ContractState> =
        Generator.replicatePoisson(2.0, publicKeyGenerator).combine(Generator.long()) { participants, nonce ->
            GeneratedState(nonce, participants.map { AnonymousParty(it) })
        }

fun commandGenerator(partiesToPickFrom: Collection<Party>): Generator<Pair<Command<*>, Party>> {
    return pickOneOrMaybeNew(partiesToPickFrom, partyGenerator).combine(Generator.long()) { signer, nonce ->
        Pair(
                Command(GeneratedCommandData(nonce), signer.owningKey),
                signer
        )
    }
}

val partyGenerator: Generator<Party> = Generator.int().combine(publicKeyGenerator) { n, key ->
    Party(getTestX509Name("Party$n"), key)
}

fun <A> pickOneOrMaybeNew(from: Collection<A>, generator: Generator<A>): Generator<A> {
    if (from.isEmpty()) {
        return generator
    } else {
        return generator.flatMap {
            Generator.pickOne(from + it)
        }
    }
}

val attachmentGenerator: Generator<Attachment> = Generator.bytes(16).map(::GeneratedAttachment)
val outputsGenerator = Generator.replicatePoisson(3.0, stateGenerator)
