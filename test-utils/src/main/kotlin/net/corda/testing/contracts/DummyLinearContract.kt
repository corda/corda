package net.corda.testing.contracts

import net.corda.core.contracts.*
import net.corda.core.contracts.clauses.Clause
import net.corda.core.contracts.clauses.FilterOn
import net.corda.core.contracts.clauses.verifyClause
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.containsAny
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.schemas.testing.DummyLinearStateSchemaV1
import net.corda.core.schemas.testing.DummyLinearStateSchemaV2
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC

class DummyLinearContract : Contract {
    override val legalContractReference: SecureHash = SecureHash.sha256("Test")

    val clause: Clause<State, CommandData, Unit> = LinearState.ClauseVerifier()
    override fun verify(tx: TransactionForContract) = verifyClause(tx,
            FilterOn(clause, { states -> states.filterIsInstance<State>() }),
            emptyList())

    data class State(
            override val linearId: UniqueIdentifier = UniqueIdentifier(),
            override val contract: Contract = DummyLinearContract(),
            override val participants: List<AbstractParty> = listOf(),
            val linearString: String = "ABC",
            val linearNumber: Long = 123L,
            val linearTimestamp: java.time.Instant = LocalDateTime.now().toInstant(UTC),
            val linearBoolean: Boolean = true,
            val nonce: SecureHash = SecureHash.randomSHA256()) : LinearState, QueryableState {

        override fun isRelevant(ourKeys: Set<java.security.PublicKey>): Boolean {
            return participants.any { it.owningKey.containsAny(ourKeys) }
        }

        override fun supportedSchemas(): Iterable<MappedSchema> = listOf(DummyLinearStateSchemaV1, DummyLinearStateSchemaV2)

        override fun generateMappedObject(schema: MappedSchema): PersistentState {
            return when (schema) {
                is DummyLinearStateSchemaV1 -> DummyLinearStateSchemaV1.PersistentDummyLinearState(
                        externalId = linearId.externalId,
                        uuid = linearId.id,
                        linearString = linearString,
                        linearNumber = linearNumber,
                        linearTimestamp = linearTimestamp,
                        linearBoolean = linearBoolean
                )
                is DummyLinearStateSchemaV2 -> DummyLinearStateSchemaV2.PersistentDummyLinearState(
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