import java.security.PublicKey

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// Cash


// TODO: Implement multi-issuer case.
// TODO: Does multi-currency also make sense? Probably?
// TODO: Implement a generate function.

// Just a fake program identifier for now. In a real system it could be, for instance, the hash of the program bytecode.
val CASH_PROGRAM_ID = SecureHash.sha256("cash")

/** A state representing a claim on the cash reserves of some institution */
data class CashState(
    /** The institution that has this original cash deposit (propagated) */
    val issuingInstitution: Institution,

    /** Whatever internal ID the bank needs in order to locate that deposit, may be encrypted (propagated) */
    val depositReference: ByteArray,

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
        // Select all input states that are cash states and ensure they are all denominated in the same currency and
        // issued by the same issuer.
        val inputs = inStates.filterIsInstance<CashState>()
        val inputMoney = inputs.sumBy { it.amount.pennies }

        requireThat {
            "there is at least one cash input" by inputs.isNotEmpty()
            "all inputs use the same currency" by (inputs.groupBy { it.amount.currency }.size == 1)
            "all inputs come from the same issuer" by (inputs.groupBy { it.issuingInstitution }.size == 1)
            "some money is actually moving" by (inputMoney > 0)
        }

        val issuer = inputs.first().issuingInstitution
        val currency = inputs.first().amount.currency
        val depositReference = inputs.first().depositReference

        // Select all the output states that are cash states. There may be zero if all money is being withdrawn.
        // If there are any though, check that the currencies and issuers match the inputs.
        val outputs = outStates.filterIsInstance<CashState>()
        val outputMoney = outputs.sumBy { it.amount.pennies }
        requireThat {
            "all outputs use the currency of the inputs"               by outputs.all { it.amount.currency == currency }
            "all outputs claim against the issuer of the inputs"       by outputs.all { it.issuingInstitution == issuer }
            "all outputs use the same deposit reference as the inputs" by outputs.all { it.depositReference == depositReference }
        }

        // If we have any commands, find the one that came from the issuer of the original cash deposit and
        // check if it's an exit command.
        val issuerCommand = args.find { it.signingInstitution == issuer }?.command as? ExitCashCommand
        val amountExitingLedger = issuerCommand?.amount?.pennies ?: 0
        requireThat("the value exiting the ledger is not more than the input value", amountExitingLedger <= outputMoney)
        
        // Verify the books balance.
        requireThat("the amounts balance", inputMoney == outputMoney + amountExitingLedger)

        // Now check the digital signatures on the move commands. Every input has an owning public key, and we must
        // see a signature from each of those keys. The actual signatures have been verified against the transaction
        // data by the platform before execution.
        val owningPubKeys  = inputs.map  { it.owner }.toSortedSet()
        val keysThatSigned = args.filter { it.command is MoveCashCommand }.map { it.signer }.toSortedSet()
        requireThat("the owning keys are the same as the signing keys", owningPubKeys == keysThatSigned)

        // Accept.
    }
}