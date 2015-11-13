package contracts

import core.*
import java.security.PublicKey
import java.util.*

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// Cash
//
// Open issues:
//  - Cannot do currency exchanges this way, as the contract insists that there be a single currency involved!
//  - Nothing ensures cash states are not created out of thin air.
//  - Complex logic to do grouping: can it be generalised out into platform code?

// Just a fake program identifier for now. In a real system it could be, for instance, the hash of the program bytecode.
val CASH_PROGRAM_ID = SecureHash.sha256("cash")

class InsufficientBalanceException(val amountMissing: Amount) : Exception()

/**
 * A cash transaction may split and merge money represented by a set of (issuer, depositRef) pairs, across multiple
 * input and output states. Imagine a Bitcoin transaction but in which all UTXOs had a colour
 * (a blend of issuer+depositRef) and you couldn't merge outputs of two colours together, but you COULD put them in
 * the same transaction.
 *
 * The goal of this design is to ensure that money can be withdrawn from the ledger easily: if you receive some money
 * via this contract, you always know where to go in order to extract it from the R3 ledger via a regular wire transfer,
 * no matter how many hands it has passed through in the intervening time.
 *
 * At the same time, other contracts that just want money and don't care much who is currently holding it in their
 * vaults can ignore the issuer/depositRefs and just examine the amount fields.
 */
object Cash : Contract {
    override val legalContractReference: String = "https://www.big-book-of-banking-law.gov/cash-claims.html"

    /** A state representing a cash claim against some institution */
    data class State(
            /** Where the underlying currency backing this ledger entry can be found (propagated) */
            val deposit: InstitutionReference,

            val amount: Amount,

            /** There must be a MoveCommand signed by this key to claim the amount */
            val owner: PublicKey
    ) : ContractState {
        override val programRef = CASH_PROGRAM_ID
        override fun toString() = "Cash($amount at $deposit owned by $owner)"
    }


    sealed class Commands {
        /** A command proving ownership of some input states, the signature covers the output states. */
        object Move : Command

        /**
         * A command stating that money has been withdrawn from the shared ledger and is now accounted for
         * in some other way.
         */
        data class Exit(val amount: Amount) : Command
    }

    /** This is the function EVERYONE runs */
    override fun verify(tx: TransactionForVerification) {
        with(tx) {
            val cashInputs = inStates.filterIsInstance<Cash.State>()

            requireThat {
                "there is at least one cash input" by cashInputs.isNotEmpty()
                "there are no zero sized inputs" by cashInputs.none { it.amount.pennies == 0 }
                "all inputs use the same currency" by (cashInputs.groupBy { it.amount.currency }.size == 1)
            }

            val currency = cashInputs.first().amount.currency

            // Select all the output states that are cash states. There may be zero if all money is being withdrawn.
            val cashOutputs = outStates.filterIsInstance<Cash.State>()
            requireThat {
                "all outputs use the currency of the inputs" by cashOutputs.all { it.amount.currency == currency }
            }

            // For each deposit that's represented in the inputs, group the inputs together and verify that the outputs
            // balance, taking into account a possible exit command from that issuer.
            var outputsLeft = cashOutputs.size
            for ((deposit, inputs) in cashInputs.groupBy { it.deposit }) {
                val outputs = cashOutputs.filter { it.deposit == deposit }
                outputsLeft -= outputs.size

                val inputAmount = inputs.map { it.amount }.sumOrThrow()
                val outputAmount = outputs.map { it.amount }.sumOrZero(currency)

                val issuerCommand = args.select<Commands.Exit>(institution = deposit.institution).singleOrNull()
                val amountExitingLedger = issuerCommand?.value?.amount ?: Amount(0, inputAmount.currency)

                requireThat {
                    "for deposit ${deposit.reference} at issuer ${deposit.institution.name} the amounts balance" by (inputAmount == outputAmount + amountExitingLedger)
                }
            }

            requireThat { "no output states are unaccounted for" by (outputsLeft == 0) }

            // Now check the digital signatures on the move commands. Every input has an owning public key, and we must
            // see a signature from each of those keys. The actual signatures have been verified against the transaction
            // data by the platform before execution.
            val owningPubKeys  = cashInputs.map  { it.owner }.toSortedSet()
            val keysThatSigned = args.requireSingleCommand<Commands.Move>().signers.toSortedSet()
            requireThat {
                "the owning keys are the same as the signing keys" by (owningPubKeys == keysThatSigned)
            }

            // Accept.
        }
    }

    // TODO: craftSpend should work more like in bitcoinj, where it takes and modifies a transaction template.
    // This would allow multiple contracts to compose properly (e.g. bond trade+cash movement).

    /** Generate a transaction that consumes one or more of the given input states to move money to the given pubkey. */
    @Throws(InsufficientBalanceException::class)
    fun craftSpend(amount: Amount, to: PublicKey, wallet: List<Cash.State>): TransactionForTest {
        // Discussion
        //
        // This code is analogous to the Wallet.send() set of methods in bitcoinj, and has the same general outline.
        //
        // First we must select a set of cash states (which for convenience we will call 'coins' here, as in bitcoinj).
        // The input states can be considered our "wallet", and may consist of coins of different currencies, and from
        // different institutions and deposits.
        //
        // Coin selection is a complex problem all by itself and many different approaches can be used. It is easily
        // possible for different actors to use different algorithms and approaches that, for example, compete on
        // privacy vs efficiency (number of states created). Some spends may be artificial just for the purposes of
        // obfuscation and so on.
        //
        // Having selected coins of the right currency, we must craft output states for the amount we're sending and
        // the "change", which goes back to us. The change is required to make the amounts balance. We may need more
        // than one change output in order to avoid merging coins from different deposits.
        //
        // Once we've selected our inputs and generated our outputs, we must calculate a signature for each key that
        // appears in the input set. Same as with Bitcoin, ideally keys are never reused for privacy reasons, but we
        // must handle the case where they are. Once the signatures are generated, a MoveCommand for each key/sig pair
        // is put into the transaction, which is finally returned.

        val currency = amount.currency
        val coinsOfCurrency = wallet.filter { it.amount.currency == currency }

        val gathered = arrayListOf<State>()
        var gatheredAmount = Amount(0, currency)
        for (c in coinsOfCurrency) {
            if (gatheredAmount >= amount) break
            gathered.add(c)
            gatheredAmount += c.amount
        }

        if (gatheredAmount < amount)
            throw InsufficientBalanceException(amount - gatheredAmount)

        val change = gatheredAmount - amount
        val keysUsed = gathered.map { it.owner }.toSet()

        val states = gathered.groupBy { it.deposit }.map {
            val (deposit, coins) = it
            val totalAmount = coins.map { it.amount }.sumOrThrow()
            State(deposit, totalAmount, to)
        }

        val outputs = if (change.pennies > 0) {
            // Just copy a key across as the change key. In real life of course, this works but leaks private data.
            // In bitcoinj we derive a fresh key here and then shuffle the outputs to ensure it's hard to follow
            // value flows through the transaction graph.
            val changeKey = gathered.first().owner
            // Add a change output and adjust the last output downwards.
            states.subList(0, states.lastIndex) +
                    states.last().let { it.copy(amount = it.amount - change) } +
                    State(gathered.last().deposit, change, changeKey)
        } else states

        // Finally, generate the commands. Pretend to sign here, real signatures aren't done yet.
        val commands = keysUsed.map { AuthenticatedObject(listOf(it), emptyList(), Commands.Move) }

        return TransactionForTest(gathered.toArrayList(), outputs.toArrayList(), commands.toArrayList())
    }
}

// Small DSL extension.
fun Iterable<ContractState>.sumCashBy(owner: PublicKey) = filterIsInstance<Cash.State>().filter { it.owner == owner }.map { it.amount }.sumOrThrow()
fun Iterable<ContractState>.sumCash() = filterIsInstance<Cash.State>().map { it.amount }.sumOrThrow()
fun Iterable<ContractState>.sumCashOrNull() = filterIsInstance<Cash.State>().map { it.amount }.sumOrNull()
fun Iterable<ContractState>.sumCashOrZero(currency: Currency) = filterIsInstance<Cash.State>().map { it.amount }.sumOrZero(currency)
