package net.corda.bn.demo.contracts

import net.corda.bn.states.BNIdentity
import net.corda.bn.states.BNPermission
import net.corda.bn.states.BNRole
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.util.regex.Pattern

/**
 * Represents loan between [lender] and [borrower] party on ledger.
 *
 * @property lender Party issuing the loan.
 * @property borrower Party paying of the loan.
 * @property amount Amount of loan left to be paid.
 * @property networkId ID of the Business Network loan is part of.
 */
@BelongsToContract(LoanContract::class)
data class LoanState(
        val lender: Party,
        val borrower: Party,
        val amount: Int,
        val networkId: String,
        override val linearId: UniqueIdentifier = UniqueIdentifier(),
        override val participants: List<AbstractParty> = listOf(lender, borrower)
) : LinearState {

    /** Helper method used to create new [LoanState] with decreased [amount] field by [amountToSettle]. **/
    fun settle(amountToSettle: Int) = copy(amount = amount - amountToSettle)
}

/**
 * Represents Loan Issuer role which has permission to issue loans.
 */
@CordaSerializable
class LoanIssuerRole : BNRole("LoanIssuer", setOf(LoanPermissions.CAN_ISSUE_LOAN))

/**
 * Loan related permissions which can be given to a role.
 */
@CordaSerializable
enum class LoanPermissions : BNPermission {

    /** Enables Business Network member to issue [LoanState]s. **/
    CAN_ISSUE_LOAN
}

/**
 * Business identity specific for banks. Uses Swift Business Identifier Code (BIC).
 *
 * @property bic Business Identifier Code of the bank.
 */
@CordaSerializable
data class BankIdentity(val bic: String) : BNIdentity {
    companion object {
        private const val bicRegex = "^[a-zA-Z]{6}[0-9a-zA-Z]{2}([0-9a-zA-Z]{3})?$"
    }

    /** Checks whether provided BIC is valid. **/
    fun isValid() = bic.matches(Pattern.compile(bicRegex).toRegex())
}