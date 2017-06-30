package net.corda.contracts.asset

import net.corda.contracts.clause.AbstractConserveAmount
import net.corda.contracts.clause.AbstractIssue
import net.corda.contracts.clause.NoZeroSizedOutputs
import net.corda.core.contracts.*
import net.corda.core.contracts.clauses.AllOf
import net.corda.core.contracts.clauses.FirstOf
import net.corda.core.contracts.clauses.GroupClauseVerifier
import net.corda.core.contracts.clauses.verifyClause
import net.corda.core.crypto.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.Emoji
import net.corda.schemas.SampleCashSchemaV1
import net.corda.schemas.SampleCashSchemaV2
import net.corda.schemas.SampleCashSchemaV3
import java.util.*

class DummyFungibleContract : OnLedgerAsset<Currency, DummyFungibleContract.Commands, DummyFungibleContract.State>() {
    override val legalContractReference: SecureHash = SecureHash.sha256("https://www.big-book-of-banking-law.gov/cash-claims.html")

    override fun extractCommands(commands: Collection<AuthenticatedObject<CommandData>>): List<AuthenticatedObject<DummyFungibleContract.Commands>>
            = commands.select<DummyFungibleContract.Commands>()

    interface Clauses {
        class Group : GroupClauseVerifier<State, Commands, Issued<Currency>>(AllOf<State, Commands, Issued<Currency>>(
                NoZeroSizedOutputs<State, Commands, Currency>(),
                FirstOf<State, Commands, Issued<Currency>>(
                        Issue(),
                        ConserveAmount())
        )
        ) {
            override fun groupStates(tx: TransactionForContract): List<TransactionForContract.InOutGroup<State, Issued<Currency>>>
                    = tx.groupStates<State, Issued<Currency>> { it.amount.token }
        }

        class Issue : AbstractIssue<State, Commands, Currency>(
                sum = { sumCash() },
                sumOrZero = { sumCashOrZero(it) }
        ) {
            override val requiredCommands: Set<Class<out CommandData>> = setOf(Commands.Issue::class.java)
        }

        @CordaSerializable
        class ConserveAmount : AbstractConserveAmount<State, Commands, Currency>()
    }

    data class State(
            override val amount: Amount<Issued<Currency>>,

            override val owner: AbstractParty
    ) : FungibleAsset<Currency>, QueryableState {
        constructor(deposit: PartyAndReference, amount: Amount<Currency>, owner: AbstractParty)
                : this(Amount(amount.quantity, Issued(deposit, amount.token)), owner)

        override val exitKeys = setOf(owner.owningKey, amount.token.issuer.party.owningKey)
        override val contract = CASH_PROGRAM_ID
        override val participants = listOf(owner)

        override fun move(newAmount: Amount<Issued<Currency>>, newOwner: AbstractParty): FungibleAsset<Currency>
                = copy(amount = amount.copy(newAmount.quantity), owner = newOwner)

        override fun toString() = "${Emoji.bagOfCash}Cash($amount at ${amount.token.issuer} owned by $owner)"

        override fun withNewOwner(newOwner: AbstractParty) = Pair(Commands.Move(), copy(owner = newOwner))

        /** Object Relational Mapping support. */
        override fun generateMappedObject(schema: MappedSchema): PersistentState {
            return when (schema) {
                is SampleCashSchemaV1 -> SampleCashSchemaV1.PersistentCashState(
                        owner = this.owner.owningKey.toBase58String(),
                        pennies = this.amount.quantity,
                        currency = this.amount.token.product.currencyCode,
                        issuerParty = this.amount.token.issuer.party.owningKey.toBase58String(),
                        issuerRef = this.amount.token.issuer.reference.bytes
                )
                is SampleCashSchemaV2 -> SampleCashSchemaV2.PersistentCashState(
                        _participants = this.participants.toSet(),
                        _owner = this.owner,
                        _quantity = this.amount.quantity,
                        currency = this.amount.token.product.currencyCode,
                        _issuerParty = this.amount.token.issuer.party,
                        _issuerRef = this.amount.token.issuer.reference.bytes
                )
                is SampleCashSchemaV3 -> SampleCashSchemaV3.PersistentCashState(
                        _participants = this.participants.toSet(),
                        _owner = this.owner,
                        _quantity = this.amount.quantity,
                        _currency = this.amount.token.product.currencyCode,
                        _issuerParty = this.amount.token.issuer.party,
                        _issuerRef = this.amount.token.issuer.reference.bytes
                )
                else -> throw IllegalArgumentException("Unrecognised schema $schema")
            }
        }

        /** Object Relational Mapping support. */
        override fun supportedSchemas(): Iterable<MappedSchema> = listOf(SampleCashSchemaV1, SampleCashSchemaV2, SampleCashSchemaV3)
    }

    interface Commands : FungibleAsset.Commands {

        data class Move(override val contractHash: SecureHash? = null) : FungibleAsset.Commands.Move, Commands

        data class Issue(override val nonce: Long = newSecureRandom().nextLong()) : FungibleAsset.Commands.Issue, Commands

        data class Exit(override val amount: Amount<Issued<Currency>>) : Commands, FungibleAsset.Commands.Exit<Currency>
    }

    fun generateIssue(tx: TransactionBuilder, tokenDef: Issued<Currency>, pennies: Long, owner: AbstractParty, notary: Party)
            = generateIssue(tx, Amount(pennies, tokenDef), owner, notary)

    fun generateIssue(tx: TransactionBuilder, amount: Amount<Issued<Currency>>, owner: AbstractParty, notary: Party)
        = generateIssue(tx, TransactionState(State(amount, owner), notary), generateIssueCommand())

    override fun deriveState(txState: TransactionState<State>, amount: Amount<Issued<Currency>>, owner: AbstractParty)
            = txState.copy(data = txState.data.copy(amount = amount, owner = owner))

    override fun generateExitCommand(amount: Amount<Issued<Currency>>) = Commands.Exit(amount)
    override fun generateIssueCommand() = Commands.Issue()
    override fun generateMoveCommand() = Commands.Move()

    override fun verify(tx: TransactionForContract)
            = verifyClause(tx, Clauses.Group(), extractCommands(tx.commands))
}

