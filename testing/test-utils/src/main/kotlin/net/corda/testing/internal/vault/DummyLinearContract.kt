/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.testing.internal.vault

import net.corda.core.contracts.Contract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.transactions.LedgerTransaction
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC

const val DUMMY_LINEAR_CONTRACT_PROGRAM_ID = "net.corda.testing.internal.vault.DummyLinearContract"

class DummyLinearContract : Contract {
    override fun verify(tx: LedgerTransaction) {
        val inputs = tx.inputs.map { it.state.data }.filterIsInstance<State>()
        val outputs = tx.outputs.map { it.data }.filterIsInstance<State>()

        val inputIds = inputs.map { it.linearId }.distinct()
        val outputIds = outputs.map { it.linearId }.distinct()
        requireThat {
            "LinearStates are not merged" using (inputIds.count() == inputs.count())
            "LinearStates are not split" using (outputIds.count() == outputs.count())
        }
    }

    data class State(
            override val linearId: UniqueIdentifier = UniqueIdentifier(),
            override val participants: List<AbstractParty> = listOf(),
            val linearString: String = "ABC",
            val linearNumber: Long = 123L,
            val linearTimestamp: java.time.Instant = LocalDateTime.now().toInstant(UTC),
            val linearBoolean: Boolean = true,
            val nonce: SecureHash = SecureHash.randomSHA256()) : LinearState, QueryableState {
        override fun supportedSchemas(): Iterable<MappedSchema> = listOf(DummyLinearStateSchemaV1, DummyLinearStateSchemaV2)
        override fun generateMappedObject(schema: MappedSchema): PersistentState {
            return when (schema) {
                is DummyLinearStateSchemaV1 -> DummyLinearStateSchemaV1.PersistentDummyLinearState(
                        participants = participants.toMutableSet(),
                        externalId = linearId.externalId,
                        uuid = linearId.id,
                        linearString = linearString,
                        linearNumber = linearNumber,
                        linearTimestamp = linearTimestamp,
                        linearBoolean = linearBoolean
                )
                is DummyLinearStateSchemaV2 -> DummyLinearStateSchemaV2.PersistentDummyLinearState(
                        participants = participants.toMutableSet(),
                        uid = linearId,
                        linearString = linearString,
                        linearNumber = linearNumber,
                        linearTimestamp = linearTimestamp,
                        linearBoolean = linearBoolean
                )
                else -> throw IllegalArgumentException("Unrecognised schema $schema")
            }
        }
    }
}