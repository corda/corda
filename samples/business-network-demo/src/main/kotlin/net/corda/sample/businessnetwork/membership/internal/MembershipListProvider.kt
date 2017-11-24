package net.corda.sample.businessnetwork.membership.internal

import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.NetworkMapCache
import net.corda.sample.businessnetwork.membership.MembershipList

object MembershipListProvider {
    fun obtainMembershipList(listName: CordaX500Name, networkMapCache: NetworkMapCache): MembershipList =
            CsvMembershipList(MembershipListProvider::class.java.getResourceAsStream("${listName.commonName}.csv"), networkMapCache)
}