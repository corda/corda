/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.sample.businessnetwork.membership.internal

import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.NetworkMapCache
import net.corda.sample.businessnetwork.membership.flow.MembershipList

object MembershipListProvider {
    fun obtainMembershipList(listName: CordaX500Name, networkMapCache: NetworkMapCache): MembershipList =
            CsvMembershipList(MembershipListProvider::class.java.getResourceAsStream("${listName.organisation}.csv"), networkMapCache)
}