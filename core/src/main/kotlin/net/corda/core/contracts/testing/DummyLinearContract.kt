package net.corda.core.contracts.testing

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.clauses.FilterOn
import net.corda.core.crypto.containsAny
import net.corda.core.schemas.testing.DummyLinearStateSchemaV1
import net.corda.core.schemas.testing.DummyLinearStateSchemaV2

class DummyLinearContract : net.corda.core.contracts.Contract {
    override val legalContractReference: net.corda.core.crypto.SecureHash = net.corda.core.crypto.SecureHash.Companion.sha256("Test")

    val clause: net.corda.core.contracts.clauses.Clause<State, CommandData, Unit> = net.corda.core.contracts.LinearState.ClauseVerifier()
    override fun verify(tx: net.corda.core.contracts.TransactionForContract) = net.corda.core.contracts.clauses.verifyClause(tx,
            FilterOn(clause, { states -> states.filterIsInstance<State>() }),
            emptyList())

    data class State(
            override val linearId: net.corda.core.contracts.UniqueIdentifier = net.corda.core.contracts.UniqueIdentifier(),
            override val contract: net.corda.core.contracts.Contract = net.corda.core.contracts.testing.DummyLinearContract(),
            override val participants: List<net.corda.core.identity.AbstractParty> = listOf(),
            val linearString: String = "ABC",
            val linearNumber: Long = 123L,
            val linearTimestamp: java.time.Instant = java.time.LocalDateTime.now().toInstant(java.time.ZoneOffset.UTC),
            val linearBoolean: Boolean = true,
            val nonce: net.corda.core.crypto.SecureHash = net.corda.core.crypto.SecureHash.Companion.randomSHA256()) : net.corda.core.contracts.LinearState, net.corda.core.schemas.QueryableState {

        override fun isRelevant(ourKeys: Set<java.security.PublicKey>): Boolean {
            return participants.any { it.owningKey.containsAny(ourKeys) }
        }

        override fun supportedSchemas(): Iterable<net.corda.core.schemas.MappedSchema> = listOf(DummyLinearStateSchemaV1, DummyLinearStateSchemaV2)

        override fun generateMappedObject(schema: net.corda.core.schemas.MappedSchema): net.corda.core.schemas.PersistentState {
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