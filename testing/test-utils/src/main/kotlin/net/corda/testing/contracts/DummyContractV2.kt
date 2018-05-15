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

import net.corda.core.contracts.AlwaysAcceptAttachmentConstraint
import net.corda.core.contracts.AttachmentConstraint
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.ContractClassName
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.UpgradedContractWithLegacyConstraint
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction

// The dummy contract doesn't do anything useful. It exists for testing purposes.

/**
 * Dummy contract state for testing of the upgrade process.
 */
// DOCSTART 1
class DummyContractV2 : UpgradedContractWithLegacyConstraint<DummyContract.State, DummyContractV2.State> {
    companion object {
        const val PROGRAM_ID: ContractClassName = "net.corda.testing.contracts.DummyContractV2"
    }

    override val legacyContract: String = DummyContract::class.java.name
    override val legacyContractConstraint: AttachmentConstraint = AlwaysAcceptAttachmentConstraint

    data class State(val magicNumber: Int = 0, val owners: List<AbstractParty>) : ContractState {
        override val participants: List<AbstractParty> = owners
    }

    interface Commands : CommandData {
        class Create : TypeOnlyCommandData(), Commands
        class Move : TypeOnlyCommandData(), Commands
    }

    override fun upgrade(state: DummyContract.State): State {
        return State(state.magicNumber, state.participants)
    }

    override fun verify(tx: LedgerTransaction) {
        // Other verifications.
    }
    // DOCEND 1
}
