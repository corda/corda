import java.security.PublicKey
import java.util.*

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// Cash
//
// A cash transaction may split and merge money represented by a set of (issuer, depositRef) pairs, across multiple
// input and output states. Imagine a Bitcoin transaction but in which all UTXOs had a colour
// (a blend of issuer+depositRef) and you couldn't merge outputs of two colours together, but you COULD put them in
// the same transaction.
//
// The goal of this design is to ensure that money can be withdrawn from the ledger easily: if you receive some money
// via this contract, you always know where to go in order to extract it from the R3 ledger via a regular wire transfer,
// no matter how many hands it has passed through in the intervening time.
//
// At the same time, other contracts that just want money and don't care much who is currently holding it in their
// vaults can ignore the issuer/depositRefs and just examine the amount fields.

// TODO: Does multi-currency also make sense? Probably?
// TODO: Implement a generate function.

// Just a fake program identifier for now. In a real system it could be, for instance, the hash of the program bytecode.
val CASH_PROGRAM_ID = SecureHash.sha256("cash")

/** A state representing a claim on the cash reserves of some institution */
data class CashState(
    /** The institution that has this original cash deposit (propagated) */
    val issuingInstitution: Institution,

    /** Whatever internal ID the bank needs in order to locate that deposit, may be encrypted (propagated) */
    val depositReference: OpaqueBytes,

    val amount: Amount,

    /** There must be a MoveCommand signed by this key to claim the amount */
    val owner: PublicKey
) : ContractState {
    override val programRef = CASH_PROGRAM_ID
}

/** A command proving ownership of some input states, the signature covers the output states. */
class MoveCashCommand : Command
/** A command stating that money has been withdrawn from the shared ledger and is now accounted for in some other way */
class ExitCashCommand(val amount: Amount) : Command

class CashContract : Contract {
    override fun verify(inStates: List<ContractState>, outStates: List<ContractState>, args: List<VerifiedSignedCommand>) {
        val cashInputs = inStates.filterIsInstance<CashState>()

        requireThat {
            "there is at least one cash input" by cashInputs.isNotEmpty()
            "there are no zero sized inputs" by cashInputs.none { it.amount.pennies == 0 }
            "all inputs use the same currency" by (cashInputs.groupBy { it.amount.currency }.size == 1)
        }

        val currency = cashInputs.first().amount.currency

        // Select all the output states that are cash states. There may be zero if all money is being withdrawn.
        val cashOutputs = outStates.filterIsInstance<CashState>()
        requireThat {
            "all outputs use the currency of the inputs" by cashOutputs.all { it.amount.currency == currency }
        }

        // For each deposit that's represented in the inputs, group the inputs together and verify that the outputs
        // balance, taking into account a possible exit command from that issuer.
        var outputsLeft = cashOutputs.size
        for ((pair, inputs) in cashInputs.groupBy { Pair(it.issuingInstitution, it.depositReference) }) {
            val (issuer, depositRef) = pair
            val outputs = cashOutputs.filter { it.issuingInstitution == issuer && it.depositReference == depositRef }
            outputsLeft -= outputs.size

            val inputAmount = inputs.map { it.amount }.sum()
            val outputAmount = outputs.map { it.amount }.sumOrZero(currency)

            val issuerCommand = args.filter { it.signingInstitution == issuer }.map { it.command as? ExitCashCommand }.filterNotNull().singleOrNull()
            val amountExitingLedger = issuerCommand?.amount ?: Amount(0, inputAmount.currency)

            requireThat {
                "for deposit $depositRef at issuer ${issuer.name} the amounts balance" by (inputAmount == outputAmount + amountExitingLedger)
            }
        }

        requireThat { "no output states are unaccounted for" by (outputsLeft == 0) }

        // Now check the digital signatures on the move commands. Every input has an owning public key, and we must
        // see a signature from each of those keys. The actual signatures have been verified against the transaction
        // data by the platform before execution.
        val owningPubKeys  = cashInputs.map  { it.owner }.toSortedSet()
        val keysThatSigned = args.filter { it.command is MoveCashCommand }.map { it.signer }.toSortedSet()
        requireThat { "the owning keys are the same as the signing keys" by (owningPubKeys == keysThatSigned) }

        // Accept.
    }
}