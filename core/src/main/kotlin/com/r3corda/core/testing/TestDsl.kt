package com.r3corda.core.testing

import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.node.services.IdentityService
import com.r3corda.core.node.services.StorageService
import java.security.PublicKey
import java.util.*

inline fun <reified State: ContractState> ledger(
        dsl: LedgerDsl<State, TestTransactionDslInterpreter<State>, TestLedgerDslInterpreter<State>>.() -> Unit) =
        dsl(LedgerDsl(TestLedgerDslInterpreter.create()))

@Deprecated(
        message = "ledger doesn't nest, use tweak",
        replaceWith = ReplaceWith("tweak"),
        level = DeprecationLevel.ERROR)
fun <State: ContractState> TransactionDslInterpreter<State>.ledger(
        dsl: LedgerDsl<State, TestTransactionDslInterpreter<State>, TestLedgerDslInterpreter<State>>.() -> Unit) {
    this.toString()
    dsl.toString()
}

@Deprecated(
        message = "ledger doesn't nest, use tweak",
        replaceWith = ReplaceWith("tweak"),
        level = DeprecationLevel.ERROR)
fun <State: ContractState> LedgerDslInterpreter<State, TransactionDslInterpreter<State>>.ledger(
        dsl: LedgerDsl<State, TestTransactionDslInterpreter<State>, TestLedgerDslInterpreter<State>>.() -> Unit) {
    this.toString()
    dsl.toString()
}

/**
 * This interpreter builds a transaction, and [TransactionDsl.verifies] that the resolved transaction is correct. Note
 * that transactions corresponding to input states are not verified. Use [LedgerDsl.verifies] for that.
 */
data class TestTransactionDslInterpreter<State: ContractState>(
        private val ledgerInterpreter: TestLedgerDslInterpreter<State>,
        private val inputStateRefs: ArrayList<StateRef> = arrayListOf(),
        internal val outputStates: ArrayList<LabeledOutput> = arrayListOf(),
        private val attachments: ArrayList<SecureHash> = arrayListOf(),
        private val commands: ArrayList<Command> = arrayListOf(),
        private val signers: LinkedHashSet<PublicKey> = LinkedHashSet(),
        private val transactionType: TransactionType = TransactionType.General()
) : TransactionDslInterpreter<State> {

    private fun copy(): TestTransactionDslInterpreter<State> =
            TestTransactionDslInterpreter(
                    ledgerInterpreter = ledgerInterpreter.copy(),
                    inputStateRefs = ArrayList(inputStateRefs),
                    outputStates = ArrayList(outputStates),
                    attachments = ArrayList(attachments),
                    commands = ArrayList(commands),
                    signers = LinkedHashSet(signers),
                    transactionType = transactionType
            )

    internal fun toWireTransaction(): WireTransaction =
            WireTransaction(
                    inputs = inputStateRefs,
                    outputs = outputStates.map { it.state },
                    attachments = attachments,
                    commands = commands,
                    signers = signers.toList(),
                    type = transactionType
            )

    override fun input(stateLabel: String) {
        val notary = stateLabel.output.notary.owningKey
        signers.add(notary)
        inputStateRefs.add(stateLabel.outputRef)
    }

    override fun input(stateRef: StateRef) {
        val notary = ledgerInterpreter.resolveStateRef(stateRef).notary
        signers.add(notary.owningKey)
        inputStateRefs.add(stateRef)
    }

    override fun output(label: String?, notary: Party, contractState: State) {
        outputStates.add(LabeledOutput(label, TransactionState(contractState, notary)))
    }

    override fun attachment(attachmentId: SecureHash) {
        attachments.add(attachmentId)
    }

    override fun _command(signers: List<PublicKey>, commandData: CommandData) {
        this.signers.addAll(signers)
        commands.add(Command(commandData, signers))
    }

    override fun _verifies(identityService: IdentityService) {
        val resolvedTransaction = ledgerInterpreter.resolveWireTransaction(toWireTransaction(), identityService)
        resolvedTransaction.verify()
    }

    override fun failsWith(expectedMessage: String?, identityService: IdentityService) {
        val exceptionThrown = try {
            _verifies(identityService)
            false
        } catch (exception: Exception) {
            if (expectedMessage != null) {
                val exceptionMessage = exception.message
                if (exceptionMessage == null) {
                    throw AssertionError(
                            "Expected exception containing '$expectedMessage' but raised exception had no message"
                    )
                } else if (!exceptionMessage.toLowerCase().contains(expectedMessage.toLowerCase())) {
                    throw AssertionError(
                            "Expected exception containing '$expectedMessage' but raised exception was '$exceptionMessage'"
                    )
                }
            }
            true
        }

        if (!exceptionThrown) {
            throw AssertionError("Expected exception but didn't get one")
        }
    }

    override fun tweak(dsl: TransactionDsl<State, TransactionDslInterpreter<State>>.() -> Unit) =
            dsl(TransactionDsl(copy()))

    override fun retrieveOutputStateAndRef(label: String): StateAndRef<State>? =
            ledgerInterpreter.labelToOutputStateAndRefs[label]
}

class AttachmentResolutionException(val attachmentId: SecureHash) :
        Exception("Attachment with id $attachmentId not found")

data class TestLedgerDslInterpreter<State: ContractState> private constructor (
        internal val stateClazz: Class<State>,
        internal val labelToOutputStateAndRefs: HashMap<String, StateAndRef<State>> = HashMap(),
        private val transactionWithLocations: HashMap<SecureHash, WireTransactionWithLocation> = HashMap(),
        private val nonVerifiedTransactionWithLocations: HashMap<SecureHash, WireTransactionWithLocation> = HashMap(),
        private val attachments: HashMap<SecureHash, Attachment> = HashMap()
) : LedgerDslInterpreter<State, TestTransactionDslInterpreter<State>> {

    // We specify [labelToOutputStateAndRefs] just so that Kotlin picks the primary constructor instead of cycling
    constructor(stateClazz: Class<State>) : this(stateClazz, labelToOutputStateAndRefs = HashMap())

    companion object {
        /**
         * Convenience factory to avoid having to pass in the Class
         */
        inline fun <reified State: ContractState> create() = TestLedgerDslInterpreter(State::class.java)

        private fun getCallerLocation(offset: Int): String {
            val stackTraceElement = Thread.currentThread().stackTrace[3 + offset]
            return stackTraceElement.toString()
        }
    }

    private data class WireTransactionWithLocation(val transaction: WireTransaction, val location: String)
    private class VerifiesFailed(transactionLocation: String, cause: Throwable) :
            Exception("Transaction defined at ($transactionLocation) didn't verify: $cause", cause)

    internal fun copy(): TestLedgerDslInterpreter<State> =
            TestLedgerDslInterpreter(
                    stateClazz = stateClazz,
                    labelToOutputStateAndRefs = HashMap(labelToOutputStateAndRefs),
                    transactionWithLocations = HashMap(transactionWithLocations),
                    nonVerifiedTransactionWithLocations = HashMap(nonVerifiedTransactionWithLocations),
                    attachments = HashMap(attachments)
            )

    fun resolveWireTransaction(wireTransaction: WireTransaction, identityService: IdentityService): TransactionForVerification {
        return wireTransaction.run {
            val authenticatedCommands = commands.map {
                AuthenticatedObject(it.signers, it.signers.mapNotNull { identityService.partyFromKey(it) }, it.value)
            }
            val resolvedInputStates = inputs.map { resolveStateRef(it) }
            val resolvedAttachments = attachments.map { resolveAttachment(it) }
            TransactionForVerification(
                    inputs = resolvedInputStates,
                    outputs = outputs,
                    commands = authenticatedCommands,
                    origHash = wireTransaction.serialized.hash,
                    attachments = resolvedAttachments,
                    signers = signers.toList(),
                    type = type
            )

        }
    }

    fun resolveStateRef(stateRef: StateRef): TransactionState<State> {
        val transactionWithLocation =
                transactionWithLocations[stateRef.txhash] ?:
                nonVerifiedTransactionWithLocations[stateRef.txhash] ?:
                throw TransactionResolutionException(stateRef.txhash)
        val output = transactionWithLocation.transaction.outputs[stateRef.index]
        return if (stateClazz.isInstance(output.data)) @Suppress("UNCHECKED_CAST") {
            output as TransactionState<State>
        } else {
            throw IllegalArgumentException("Referenced state is of another type than requested")
        }
    }

    fun resolveAttachment(attachmentId: SecureHash): Attachment =
            attachments[attachmentId] ?: throw AttachmentResolutionException(attachmentId)

    private fun interpretTransactionDsl(dsl: TransactionDsl<State, TestTransactionDslInterpreter<State>>.() -> Unit):
            TestTransactionDslInterpreter<State> {
        val transactionInterpreter = TestTransactionDslInterpreter(this)
        dsl(TransactionDsl(transactionInterpreter))
        return transactionInterpreter
    }

    private fun toTransactionGroup(identityService: IdentityService, storageService: StorageService): TransactionGroup {
        val ledgerTransactions = transactionWithLocations.map {
            it.value.transaction.toLedgerTransaction(identityService, storageService.attachments)
        }
        val nonVerifiedLedgerTransactions = nonVerifiedTransactionWithLocations.map {
            it.value.transaction.toLedgerTransaction(identityService, storageService.attachments)
        }
        return TransactionGroup(ledgerTransactions.toSet(), nonVerifiedLedgerTransactions.toSet())
    }

    private fun recordTransactionWithTransactionMap(
            dsl: TransactionDsl<State, TestTransactionDslInterpreter<State>>.() -> Unit,
            transactionMap: HashMap<SecureHash, WireTransactionWithLocation> = HashMap()
    ) {
        val transactionLocation = getCallerLocation(3)
        val transactionInterpreter = interpretTransactionDsl(dsl)
        // Create the WireTransaction
        val wireTransaction = transactionInterpreter.toWireTransaction()
        // Record the output states
        transactionInterpreter.outputStates.forEachIndexed { index, labeledOutput ->
            if (labeledOutput.label != null) {
                labelToOutputStateAndRefs[labeledOutput.label] = wireTransaction.outRef(index)
            }
        }

        transactionMap[wireTransaction.serialized.hash] =
                WireTransactionWithLocation(wireTransaction, transactionLocation)

    }

    override fun transaction(dsl: TransactionDsl<State, TestTransactionDslInterpreter<State>>.() -> Unit) =
            recordTransactionWithTransactionMap(dsl, transactionWithLocations)

    override fun nonVerifiedTransaction(dsl: TransactionDsl<State, TestTransactionDslInterpreter<State>>.() -> Unit) =
        recordTransactionWithTransactionMap(dsl, nonVerifiedTransactionWithLocations)

    override fun tweak(
            dsl: LedgerDsl<State, TestTransactionDslInterpreter<State>,
                    LedgerDslInterpreter<State, TestTransactionDslInterpreter<State>>>.() -> Unit) =
            dsl(LedgerDsl(copy()))

    override fun attachment(attachment: Attachment): SecureHash {
        attachments[attachment.id] = attachment
        return attachment.id
    }

    override fun _verifies(identityService: IdentityService, storageService: StorageService) {
        val transactionGroup = toTransactionGroup(identityService, storageService)
        try {
            transactionGroup.verify()
        } catch (exception: TransactionVerificationException) {
            throw VerifiesFailed(transactionWithLocations[exception.tx.origHash]?.location ?: "<unknown>", exception)
        }
    }
}

fun main(args: Array<String>) {
    ledger<ContractState> {
        nonVerifiedTransaction {
            output("hello") { DummyLinearState() }
        }

        transaction {
            input("hello")
            tweak {
                timestamp(TEST_TX_TIME, MEGA_CORP_PUBKEY)
                fails()
            }
        }

        tweak {

            transaction {
                input("hello")
                timestamp(TEST_TX_TIME, MEGA_CORP_PUBKEY)
                fails()
            }
        }

        verifies()
    }
}
