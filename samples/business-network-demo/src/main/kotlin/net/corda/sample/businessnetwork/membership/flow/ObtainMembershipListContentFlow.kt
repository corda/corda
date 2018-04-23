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
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.unwrap

/**
 * Flow to obtain content of the membership lists this node belongs to.
 */
@StartableByRPC
@InitiatingFlow
class ObtainMembershipListContentFlow(private val membershipListName: CordaX500Name) : FlowLogic<Set<AbstractParty>>() {
    @Suspendable
    override fun call(): Set<AbstractParty> {
        val bnoParty = serviceHub.networkMapCache.getPeerByLegalName(membershipListName) ?:
                throw InvalidMembershipListNameException(membershipListName)
        val untrustworthyData = initiateFlow(bnoParty).receive<Set<AbstractParty>>()
        return untrustworthyData.unwrap { it }
    }
}

@InitiatedBy(ObtainMembershipListContentFlow::class)
class OwnerSideObtainMembershipListContentFlow(private val initiatingPartySession: FlowSession) : FlowLogic<Unit>(), MembershipAware {
    @Suspendable
    override fun call() {
        // Checking whether the calling party is a member. If not it is not even in position to enquire about membership list content.
        initiatingPartySession.counterparty.checkMembership(ourIdentity.name, this)

        val membershipListContent: Set<AbstractParty> = getMembershipList(ourIdentity.name, serviceHub).content()
        initiatingPartySession.send(membershipListContent)
    }
}