package contracts

import core.*
import java.security.PublicKey
import java.time.Instant

/**
 * This is an ultra-trivial implementation of commercial paper, which is essentially a simpler version of a corporate
 * bond. It can be seen as a company-specific currency. A company issues CP with a particular face value, say $100,
 * but sells it for less, say $90. The paper can be redeemed for cash at a given date in the future. Thus this example
 * would have a 10% interest rate with a single repayment. Commercial paper is often rolled over (redeemed and the
 * money used to immediately rebuy).
 *
 * This contract is not intended to realistically model CP. It is here only to act as a next step up above cash in
 * the prototyping phase. It is thus very incomplete.
 *
 * Open issues:
 *  - In this model, you cannot merge or split CP. Can you do this normally? We could model CP as a specialised form
 *    of cash, or reuse some of the cash code? Waiting on response from Ayoub and Rajar about whether CP can always
 *    be split/merged or only in secondary markets. Even if current systems can't do this, would it be a desirable
 *    feature to have anyway?
 */

val CP_PROGRAM_ID = SecureHash.sha256("replace-me-later-with-bytecode-hash")

// TODO: Generalise the notion of an owned instrument into a superclass/supercontract. Consider composition vs inheritance.
class CommercialPaper : Contract {
    // TODO: should reference the content of the legal agreement, not its URI
    override val legalContractReference: SecureHash = SecureHash.sha256("https://en.wikipedia.org/wiki/Commercial_paper")

    data class State(
        val issuance: InstitutionReference,
        val owner: PublicKey,
        val faceValue: Amount,
        val maturityDate: Instant
    ) : ContractState {
        override val programRef = CP_PROGRAM_ID

        fun withoutOwner() = copy(owner = NullPublicKey)
    }

    sealed class Commands : Command {
        object Move : Commands()
        object Redeem : Commands()
    }

    override fun verify(tx: TransactionForVerification) {
        // Group by everything except owner: any modification to the CP at all is considered changing it fundamentally.
        val groups = tx.groupStates<State>() { it.withoutOwner() }

        // There are two possible things that can be done with this CP. The first is trading it. The second is redeeming
        // it for cash on or after the maturity date.
        val command = tx.commands.requireSingleCommand<CommercialPaper.Commands>()

        for (group in groups) {
            val input = group.inputs.single()
            requireThat {
                "the transaction is signed by the owner of the CP" by (command.signers.contains(input.owner))
            }

            val output = group.outputs.singleOrNull()
            when (command.value) {
                is Commands.Move -> requireThat { "the output state is present" by (output != null) }

                is Commands.Redeem -> {
                    val received = tx.outStates.sumCashOrNull() ?: throw IllegalStateException("no cash being redeemed")
                    requireThat {
                        // Do we need to check the signature of the issuer here too?
                        "the paper must have matured" by (input.maturityDate < tx.time)
                        "the received amount equals the face value" by (received == input.faceValue)
                        "the paper must be destroyed" by (output == null)
                    }
                }
            }
        }
    }
}

