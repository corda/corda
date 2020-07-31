package net.corda.bn.demo.workflows

import co.paralleluniverse.fibers.Suspendable
import net.corda.bn.demo.contracts.BankIdentity
import net.corda.bn.demo.contracts.LoanPermissions
import net.corda.bn.flows.DatabaseService
import net.corda.bn.flows.IllegalMembershipStatusException
import net.corda.bn.flows.MembershipAuthorisationException
import net.corda.bn.flows.MembershipNotFoundException
import net.corda.bn.states.MembershipState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party

data class LoanMemberships(val lenderMembership: StateAndRef<MembershipState>, val borrowerMembership: StateAndRef<MembershipState>)

/**
 * This abstract flow is extended by any flow which will use business network membership verification methods.
 */
abstract class BusinessNetworkIntegrationFlow<T> : FlowLogic<T>() {

    /**
     * Verifies that [lender] and [borrower] are members of Business Network with [networkId] ID.
     *
     * @param networkId ID of the Business Network in which verification is performed.
     * @param lender Party issuing the loan.
     * @param borrower Party paying of the loan.
     */
    @Suspendable
    protected fun businessNetworkPartialVerification(networkId: String, lender: Party, borrower: Party): LoanMemberships {
        val bnService = serviceHub.cordaService(DatabaseService::class.java)
        val lenderMembership = bnService.getMembership(networkId, lender)
                ?: throw MembershipNotFoundException("Lender is not part of Business Network with $networkId ID")
        val borrowerMembership = bnService.getMembership(networkId, borrower)
                ?: throw MembershipNotFoundException("Borrower is not part of Business Network with $networkId ID")

        return LoanMemberships(lenderMembership, borrowerMembership)
    }

    /**
     * Verifies that [lender] and [borrower] are members of Business Network with [networkId] ID, their memberships are active, contain
     * business identity of [BankIdentity] type and that lender is authorised to issue the loan.
     *
     * @param networkId ID of the Business Network in which verification is performed.
     * @param lender Party issuing the loan.
     * @param borrower Party paying of the loan.
     */
    @Suppress("ThrowsCount")
    @Suspendable
    protected fun businessNetworkFullVerification(networkId: String, lender: Party, borrower: Party) {
        val bnService = serviceHub.cordaService(DatabaseService::class.java)

        bnService.getMembership(networkId, lender)?.state?.data?.apply {
            if (!isActive()) {
                throw IllegalMembershipStatusException("$lender is not active member of Business Network with $networkId ID")
            }
            if (identity.businessIdentity !is BankIdentity) {
                throw IllegalMembershipBusinessIdentityException("$lender business identity should be BankIdentity")
            }
            if (roles.find { LoanPermissions.CAN_ISSUE_LOAN in it.permissions } == null) {
                throw MembershipAuthorisationException("$lender is not authorised to issue loan in Business Network with $networkId ID")
            }
        } ?: throw MembershipNotFoundException("$lender is not member of Business Network with $networkId ID")

        bnService.getMembership(networkId, borrower)?.state?.data?.apply {
            if (!isActive()) {
                throw IllegalMembershipStatusException("$borrower is not active member of Business Network with $networkId ID")
            }
            if (identity.businessIdentity !is BankIdentity) {
                throw IllegalMembershipBusinessIdentityException("$borrower business identity should be BankIdentity")
            }
        } ?: throw MembershipNotFoundException("$borrower is not member of Business Network with $networkId ID")
    }
}