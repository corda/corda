/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.testing.contracts

import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty

/**
 * Dummy state for use in testing. Not part of any contract, not even the [DummyContract].
 */
data class DummyState @JvmOverloads constructor (
        /** Some information that the state represents for test purposes. **/
        val magicNumber: Int = 0,
        override val participants: List<AbstractParty> = listOf()) : ContractState {

    fun copy(magicNumber: Int = this.magicNumber) = DummyState(magicNumber)
}
