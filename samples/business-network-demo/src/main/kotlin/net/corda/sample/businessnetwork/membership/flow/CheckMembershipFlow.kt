/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.sample.businessnetwork.membership.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.unwrap

@CordaSerializable
enum class CheckMembershipResult {
    PASS,
    FAIL
}

@InitiatingFlow
class CheckMembershipFlow(private val otherParty: Party, private val membershipName: CordaX500Name) : FlowLogic<CheckMembershipResult>() {
    @Suspendable
    override fun call(): CheckMembershipResult {
        val bnoParty = serviceHub.networkMapCache.getPeerByLegalName(membershipName)
        return if (bnoParty != null) {
            // This will trigger CounterpartyCheckMembershipFlow
            val untrustworthyData = initiateFlow(bnoParty).sendAndReceive<CheckMembershipResult>(otherParty)
            untrustworthyData.unwrap { it }
        } else {
            throw InvalidMembershipListNameException(membershipName)
        }
    }
}

@InitiatedBy(CheckMembershipFlow::class)
class OwnerSideCheckMembershipFlow(private val initiatingPartySession: FlowSession) : FlowLogic<Unit>(), MembershipAware {
    @Suspendable
    override fun call() {
        val partyToCheck = initiatingPartySession.receive<Party>().unwrap { it }
        partyToCheck.checkMembership(ourIdentity.name, this)
        initiatingPartySession.send(CheckMembershipResult.PASS)
    }
}