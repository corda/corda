package net.corda.contracts.asset

import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.Strand
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.crypto.newSecureRandom
import net.corda.core.crypto.testing.NULL_PARTY
import net.corda.core.crypto.toBase58String
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.Emoji
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.StatesNotAvailableException
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.SerializationDefaults
import net.corda.core.serialization.deserialize
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.toHexString
import net.corda.core.utilities.toNonEmptySet
import net.corda.core.utilities.trace
import net.corda.schemas.CashSchemaV1
import org.bouncycastle.asn1.x500.X500Name
import java.math.BigInteger
import java.security.PublicKey
import java.sql.SQLException
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// Cash
//

// Just a fake program identifier for now. In a real system it could be, for instance, the hash of the program bytecode.
val CASH_PROGRAM_ID = Cash()

/**
 * A cash transaction may split and merge money represented by a set of (issuer, depositRef) pairs, across multiple
 * input and output states. Imagine a Bitcoin transaction but in which all UTXOs had a colour
 * (a blend of issuer+depositRef) and you couldn't merge outputs of two colours together, but you COULD put them in
 * the same transaction.
 *
 * The goal of this design is to ensure that money can be withdrawn from the ledger easily: if you receive some money
 * via this contract, you always know where to go in order to extract it from the R3 ledger, no matter how many hands
 * it has passed through in the intervening time.
 *
 * At the same time, other contracts that just want money and don't care much who is currently holding it in their
 * vaults can ignore the issuer/depositRefs and just examine the amount fields.
 */
class Cash : OnLedgerAsset<Currency, Cash.Commands, Cash.State>() {
    override fun extractCommands(commands: Collection<AuthenticatedObject<CommandData>>): List<AuthenticatedObject<Cash.Commands>>
            = commands.select<Cash.Commands>()

    // DOCSTART 1
    /** A state representing a cash claim against some party. */
    data class State(
            override val amount: Amount<Issued<Currency>>,

            /** There must be a MoveCommand signed by this key to claim the amount. */
            override val owner: AbstractParty
    ) : FungibleAsset<Currency>, QueryableState {
        constructor(deposit: PartyAndReference, amount: Amount<Currency>, owner: AbstractParty)
                : this(Amount(amount.quantity, Issued(deposit, amount.token)), owner)

        override val exitKeys = setOf(owner.owningKey, amount.token.issuer.party.owningKey)
        override val contract = CASH_PROGRAM_ID
        override val participants = listOf(owner)

        override fun move(newAmount: Amount<Issued<Currency>>, newOwner: AbstractParty): FungibleAsset<Currency>
                = copy(amount = amount.copy(newAmount.quantity), owner = newOwner)

        override fun toString() = "${Emoji.bagOfCash}Cash($amount at ${amount.token.issuer} owned by $owner)"

        override fun withNewOwner(newOwner: AbstractParty) = CommandAndState(Commands.Move(), copy(owner = newOwner))

        /** Object Relational Mapping support. */
        override fun generateMappedObject(schema: MappedSchema): PersistentState {
            return when (schema) {
                is CashSchemaV1 -> CashSchemaV1.PersistentCashState(
                        owner = this.owner,
                        pennies = this.amount.quantity,
                        currency = this.amount.token.product.currencyCode,
                        issuerParty = this.amount.token.issuer.party.owningKey.toBase58String(),
                        issuerRef = this.amount.token.issuer.reference.bytes
                )
            /** Additional schema mappings would be added here (eg. CashSchemaV2, CashSchemaV3, ...) */
                else -> throw IllegalArgumentException("Unrecognised schema $schema")
            }
        }

        /** Object Relational Mapping support. */
        override fun supportedSchemas(): Iterable<MappedSchema> = listOf(CashSchemaV1)
        /** Additional used schemas would be added here (eg. CashSchemaV2, CashSchemaV3, ...) */
    }
    // DOCEND 1

    // Just for grouping
    interface Commands : FungibleAsset.Commands {
        /**
         * A command stating that money has been moved, optionally to fulfil another contract.
         *
         * @param contract the contract this move is for the attention of. Only that contract's verify function
         * should take the moved states into account when considering whether it is valid. Typically this will be
         * null.
         */
        data class Move(override val contract: Class<out Contract>? = null) : FungibleAsset.Commands.Move, Commands

        /**
         * Allows new cash states to be issued into existence: the nonce ("number used once") ensures the transaction
         * has a unique ID even when there are no inputs.
         */
        data class Issue(override val nonce: Long = newSecureRandom().nextLong()) : FungibleAsset.Commands.Issue, Commands

        /**
         * A command stating that money has been withdrawn from the shared ledger and is now accounted for
         * in some other way.
         */
        data class Exit(override val amount: Amount<Issued<Currency>>) : Commands, FungibleAsset.Commands.Exit<Currency>
    }

    /**
     * Puts together an issuance transaction from the given template, that starts out being owned by the given pubkey.
     */
    fun generateIssue(tx: TransactionBuilder, tokenDef: Issued<Currency>, pennies: Long, owner: AbstractParty, notary: Party)
            = generateIssue(tx, Amount(pennies, tokenDef), owner, notary)

    /**
     * Puts together an issuance transaction for the specified amount that starts out being owned by the given pubkey.
     */
    fun generateIssue(tx: TransactionBuilder, amount: Amount<Issued<Currency>>, owner: AbstractParty, notary: Party)
            = generateIssue(tx, TransactionState(State(amount, owner), notary), generateIssueCommand())

    override fun deriveState(txState: TransactionState<State>, amount: Amount<Issued<Currency>>, owner: AbstractParty)
            = txState.copy(data = txState.data.copy(amount = amount, owner = owner))

    override fun generateExitCommand(amount: Amount<Issued<Currency>>) = Commands.Exit(amount)
    override fun generateIssueCommand() = Commands.Issue()
    override fun generateMoveCommand() = Commands.Move()

    override fun verify(tx: LedgerTransaction) {
        // Each group is a set of input/output states with distinct (reference, currency) attributes. These types
        // of cash are not fungible and must be kept separated for bookkeeping purposes.
        val groups = tx.groupStates { it: Cash.State -> it.amount.token }

        for ((inputs, outputs, key) in groups) {
            // Either inputs or outputs could be empty.
            val issuer = key.issuer
            val currency = key.product

            requireThat {
                "there are no zero sized outputs" using (outputs.none { it.amount.quantity == 0L })
            }

            val issueCommand = tx.commands.select<Commands.Issue>().firstOrNull()
            if (issueCommand != null) {
                verifyIssueCommand(inputs, outputs, tx, issueCommand, currency, issuer)
            } else {
                val inputAmount = inputs.sumCashOrNull() ?: throw IllegalArgumentException("there is at least one cash input for this group")
                val outputAmount = outputs.sumCashOrZero(Issued(issuer, currency))

                // If we want to remove cash from the ledger, that must be signed for by the issuer.
                // A mis-signed or duplicated exit command will just be ignored here and result in the exit amount being zero.
                val exitKeys: Set<PublicKey> = inputs.flatMap { it.exitKeys }.toSet()
                val exitCommand = tx.commands.select<Commands.Exit>(parties = null, signers = exitKeys).filter { it.value.amount.token == key }.singleOrNull()
                val amountExitingLedger = exitCommand?.value?.amount ?: Amount(0, Issued(issuer, currency))

                requireThat {
                    "there are no zero sized inputs" using inputs.none { it.amount.quantity == 0L }
                    "for reference ${issuer.reference} at issuer ${issuer.party} the amounts balance: ${inputAmount.quantity} - ${amountExitingLedger.quantity} != ${outputAmount.quantity}" using
                            (inputAmount == outputAmount + amountExitingLedger)
                }

                verifyMoveCommand<Commands.Move>(inputs, tx.commands)
            }
        }
    }

    private fun verifyIssueCommand(inputs: List<State>,
                                   outputs: List<State>,
                                   tx: LedgerTransaction,
                                   issueCommand: AuthenticatedObject<Commands.Issue>,
                                   currency: Currency,
                                   issuer: PartyAndReference) {
        // If we have an issue command, perform special processing: the group is allowed to have no inputs,
        // and the output states must have a deposit reference owned by the signer.
        //
        // Whilst the transaction *may* have no inputs, it can have them, and in this case the outputs must
        // sum to more than the inputs. An issuance of zero size is not allowed.
        //
        // Note that this means literally anyone with access to the network can issue cash claims of arbitrary
        // amounts! It is up to the recipient to decide if the backing party is trustworthy or not, via some
        // as-yet-unwritten identity service. See ADP-22 for discussion.

        // The grouping ensures that all outputs have the same deposit reference and currency.
        val inputAmount = inputs.sumCashOrZero(Issued(issuer, currency))
        val outputAmount = outputs.sumCash()
        val cashCommands = tx.commands.select<Commands.Issue>()
        requireThat {
            "the issue command has a nonce" using (issueCommand.value.nonce != 0L)
            // TODO: This doesn't work with the trader demo, so use the underlying key instead
            // "output states are issued by a command signer" by (issuer.party in issueCommand.signingParties)
            "output states are issued by a command signer" using (issuer.party.owningKey in issueCommand.signers)
            "output values sum to more than the inputs" using (outputAmount > inputAmount)
            "there is only a single issue command" using (cashCommands.count() == 1)
        }
    }

    companion object {
        // coin selection retry loop counter, sleep (msecs) and lock for selecting states
        private val MAX_RETRIES = 5
        private val RETRY_SLEEP = 100
        private val spendLock: ReentrantLock = ReentrantLock()
        /**
         * Generate a transaction that moves an amount of currency to the given pubkey.
         *
         * Note: an [Amount] of [Currency] is only fungible for a given Issuer Party within a [FungibleAsset]
         *
         * @param services The [ServiceHub] to provide access to the database session.
         * @param tx A builder, which may contain inputs, outputs and commands already. The relevant components needed
         *           to move the cash will be added on top.
         * @param amount How much currency to send.
         * @param to a key of the recipient.
         * @param onlyFromParties if non-null, the asset states will be filtered to only include those issued by the set
         *                        of given parties. This can be useful if the party you're trying to pay has expectations
         *                        about which type of asset claims they are willing to accept.
         * @return A [Pair] of the same transaction builder passed in as [tx], and the list of keys that need to sign
         *         the resulting transaction for it to be valid.
         * @throws InsufficientBalanceException when a cash spending transaction fails because
         *         there is insufficient quantity for a given currency (and optionally set of Issuer Parties).
         */
        @JvmStatic
        @Throws(InsufficientBalanceException::class)
        @Suspendable
        fun generateSpend(services: ServiceHub,
                          tx: TransactionBuilder,
                          amount: Amount<Currency>,
                          to: AbstractParty,
                          onlyFromParties: Set<AbstractParty> = emptySet()): Pair<TransactionBuilder, List<PublicKey>> {

            fun deriveState(txState: TransactionState<Cash.State>, amt: Amount<Issued<Currency>>, owner: AbstractParty)
                    = txState.copy(data = txState.data.copy(amount = amt, owner = owner))

            // Retrieve unspent and unlocked cash states that meet our spending criteria.
            val acceptableCoins = Cash.unconsumedCashStatesForSpending(services, amount, onlyFromParties, tx.notary, tx.lockId)
            return OnLedgerAsset.generateSpend(tx, amount, to, acceptableCoins,
                    { state, quantity, owner -> deriveState(state, quantity, owner) },
                    { Cash().generateMoveCommand() })

        }

        /**
         * An optimised query to gather Cash states that are available and retry if they are temporarily unavailable.
         * @param services The service hub to allow access to the database session
         * @param amount The amount of currency desired (ignoring issues, but specifying the currency)
         * @param onlyFromIssuerParties If empty the operation ignores the specifics of the issuer,
         * otherwise the set of eligible states wil be filtered to only include those from these issuers.
         * @param notary If null the notary source is ignored, if specified then only states marked
         * with this notary are included.
         * @param lockId The [net.corda.core.flows.StateMachineRunId.uuid] of the flow, which is used to soft reserve the states.
         * Also, previous outputs of the flow will be eligible as they are implicitly locked with this id until the flow completes.
         * @param withIssuerRefs If not empty the specific set of issuer references to match against.
         * @return The matching states that were found. If sufficient funds were found these will be locked,
         * otherwise what is available is returned unlocked for informational purposes.
         */
        @JvmStatic
        @Suspendable
        fun unconsumedCashStatesForSpending(services: ServiceHub,
                                            amount: Amount<Currency>,
                                            onlyFromIssuerParties: Set<AbstractParty> = emptySet(),
                                            notary: Party? = null,
                                            lockId: UUID,
                                            withIssuerRefs: Set<OpaqueBytes> = emptySet()): List<StateAndRef<Cash.State>> {

            val issuerKeysStr = onlyFromIssuerParties.fold("") { left, right -> left + "('${right.owningKey.toBase58String()}')," }.dropLast(1)
            val issuerRefsStr = withIssuerRefs.fold("") { left, right -> left + "('${right.bytes.toHexString()}')," }.dropLast(1)

            val stateAndRefs = mutableListOf<StateAndRef<Cash.State>>()

            // TODO: Need to provide a database provider independent means of performing this function.
            //       We are using an H2 specific means of selecting a minimum set of rows that match a request amount of coins:
            //       1) There is no standard SQL mechanism of calculating a cumulative total on a field and restricting row selection on the
            //          running total of such an accumulator
            //       2) H2 uses session variables to perform this accumulator function:
            //          http://www.h2database.com/html/functions.html#set
            //       3) H2 does not support JOIN's in FOR UPDATE (hence we are forced to execute 2 queries)

            for (retryCount in 1..MAX_RETRIES) {

                spendLock.withLock {
                    val statement = services.jdbcSession().createStatement()
                    try {
                        statement.execute("CALL SET(@t, 0);")

                        // we select spendable states irrespective of lock but prioritised by unlocked ones (Eg. null)
                        // the softLockReserve update will detect whether we try to lock states locked by others
                        val selectJoin = """
                        SELECT vs.transaction_id, vs.output_index, vs.contract_state, ccs.pennies, SET(@t, ifnull(@t,0)+ccs.pennies) total_pennies, vs.lock_id
                        FROM vault_states AS vs, contract_cash_states AS ccs
                        WHERE vs.transaction_id = ccs.transaction_id AND vs.output_index = ccs.output_index
                        AND vs.state_status = 0
                        AND ccs.ccy_code = '${amount.token}' and @t < ${amount.quantity}
                        AND (vs.lock_id = '$lockId' OR vs.lock_id is null)
                        """ +
                                (if (notary != null)
                                    " AND vs.notary_name = '${notary.name}'" else "") +
                                (if (onlyFromIssuerParties.isNotEmpty())
                                    " AND ccs.issuer_key IN ($issuerKeysStr)" else "") +
                                (if (withIssuerRefs.isNotEmpty())
                                    " AND ccs.issuer_ref IN ($issuerRefsStr)" else "")

                        // Retrieve spendable state refs
                        val rs = statement.executeQuery(selectJoin)
                        stateAndRefs.clear()
                        log.debug(selectJoin)
                        var totalPennies = 0L
                        while (rs.next()) {
                            val txHash = SecureHash.parse(rs.getString(1))
                            val index = rs.getInt(2)
                            val stateRef = StateRef(txHash, index)
                            val state = rs.getBytes(3).deserialize<TransactionState<Cash.State>>(context = SerializationDefaults.STORAGE_CONTEXT)
                            val pennies = rs.getLong(4)
                            totalPennies = rs.getLong(5)
                            val rowLockId = rs.getString(6)
                            stateAndRefs.add(StateAndRef(state, stateRef))
                            log.trace { "ROW: $rowLockId ($lockId): $stateRef : $pennies ($totalPennies)" }
                        }

                        if (stateAndRefs.isNotEmpty() && totalPennies >= amount.quantity) {
                            // we should have a minimum number of states to satisfy our selection `amount` criteria
                            log.trace("Coin selection for $amount retrieved ${stateAndRefs.count()} states totalling $totalPennies pennies: $stateAndRefs")

                            // With the current single threaded state machine available states are guaranteed to lock.
                            // TODO However, we will have to revisit these methods in the future multi-threaded.
                            services.vaultService.softLockReserve(lockId, (stateAndRefs.map { it.ref }).toNonEmptySet())
                            return stateAndRefs
                        }
                        log.trace("Coin selection requested $amount but retrieved $totalPennies pennies with state refs: ${stateAndRefs.map { it.ref }}")
                        // retry as more states may become available
                    } catch (e: SQLException) {
                        log.error("""Failed retrieving unconsumed states for: amount [$amount], onlyFromIssuerParties [$onlyFromIssuerParties], notary [$notary], lockId [$lockId]
                            $e.
                        """)
                    } catch (e: StatesNotAvailableException) { // Should never happen with single threaded state machine
                        stateAndRefs.clear()
                        log.warn(e.message)
                        // retry only if there are locked states that may become available again (or consumed with change)
                    } finally {
                        statement.close()
                    }
                }

                log.warn("Coin selection failed on attempt $retryCount")
                // TODO: revisit the back off strategy for contended spending.
                if (retryCount != MAX_RETRIES) {
                    Strand.sleep(RETRY_SLEEP * retryCount.toLong())
                }
            }

            log.warn("Insufficient spendable states identified for $amount")
            return stateAndRefs
        }
    }

}

// Small DSL extensions.

/**
 * Sums the cash states in the list belonging to a single owner, throwing an exception
 * if there are none, or if any of the cash states cannot be added together (i.e. are
 * different currencies or issuers).
 */
fun Iterable<ContractState>.sumCashBy(owner: AbstractParty): Amount<Issued<Currency>> = filterIsInstance<Cash.State>().filter { it.owner == owner }.map { it.amount }.sumOrThrow()

/**
 * Sums the cash states in the list, throwing an exception if there are none, or if any of the cash
 * states cannot be added together (i.e. are different currencies or issuers).
 */
fun Iterable<ContractState>.sumCash(): Amount<Issued<Currency>> = filterIsInstance<Cash.State>().map { it.amount }.sumOrThrow()

/** Sums the cash states in the list, returning null if there are none. */
fun Iterable<ContractState>.sumCashOrNull(): Amount<Issued<Currency>>? = filterIsInstance<Cash.State>().map { it.amount }.sumOrNull()

/** Sums the cash states in the list, returning zero of the given currency+issuer if there are none. */
fun Iterable<ContractState>.sumCashOrZero(currency: Issued<Currency>): Amount<Issued<Currency>> {
    return filterIsInstance<Cash.State>().map { it.amount }.sumOrZero(currency)
}

fun Cash.State.ownedBy(owner: AbstractParty) = copy(owner = owner)
fun Cash.State.issuedBy(party: AbstractParty) = copy(amount = Amount(amount.quantity, amount.token.copy(issuer = amount.token.issuer.copy(party = party))))
fun Cash.State.issuedBy(deposit: PartyAndReference) = copy(amount = Amount(amount.quantity, amount.token.copy(issuer = deposit)))
fun Cash.State.withDeposit(deposit: PartyAndReference): Cash.State = copy(amount = amount.copy(token = amount.token.copy(issuer = deposit)))

infix fun Cash.State.`owned by`(owner: AbstractParty) = ownedBy(owner)
infix fun Cash.State.`issued by`(party: AbstractParty) = issuedBy(party)
infix fun Cash.State.`issued by`(deposit: PartyAndReference) = issuedBy(deposit)
infix fun Cash.State.`with deposit`(deposit: PartyAndReference): Cash.State = withDeposit(deposit)

// Unit testing helpers. These could go in a separate file but it's hardly worth it for just a few functions.

/** A randomly generated key. */
val DUMMY_CASH_ISSUER_KEY by lazy { entropyToKeyPair(BigInteger.valueOf(10)) }
/** A dummy, randomly generated issuer party by the name of "Snake Oil Issuer" */
val DUMMY_CASH_ISSUER by lazy { Party(X500Name("CN=Snake Oil Issuer,O=R3,OU=corda,L=London,C=GB"), DUMMY_CASH_ISSUER_KEY.public).ref(1) }
/** An extension property that lets you write 100.DOLLARS.CASH */
val Amount<Currency>.CASH: Cash.State get() = Cash.State(Amount(quantity, Issued(DUMMY_CASH_ISSUER, token)), NULL_PARTY)
/** An extension property that lets you get a cash state from an issued token, under the [NULL_PARTY] */
val Amount<Issued<Currency>>.STATE: Cash.State get() = Cash.State(this, NULL_PARTY)
