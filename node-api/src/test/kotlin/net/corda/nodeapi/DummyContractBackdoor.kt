/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.PartyAndReference
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder

/**
 * This interface deliberately mirrors the one in the finance:isolated module.
 * We will actually link [AnotherDummyContract] against this interface rather
 * than the one inside isolated.jar, which means we won't need to use reflection
 * to execute the contract's generateInitial() method.
 */
interface DummyContractBackdoor {
    fun generateInitial(owner: PartyAndReference, magicNumber: Int, notary: Party): TransactionBuilder
    fun inspectState(state: ContractState): Int
}
