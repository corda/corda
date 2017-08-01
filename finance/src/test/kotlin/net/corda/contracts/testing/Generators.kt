package net.corda.contracts.testing

import com.pholser.junit.quickcheck.generator.GenerationStatus
import com.pholser.junit.quickcheck.generator.Generator
import com.pholser.junit.quickcheck.generator.java.util.ArrayListGenerator
import com.pholser.junit.quickcheck.random.SourceOfRandomness
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.Command
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TransactionType
import net.corda.core.crypto.testing.NullSignature
import net.corda.core.identity.AnonymousParty
import net.corda.core.testing.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction

/**
 * This file contains generators for quickcheck style testing. The idea is that we can write random instance generators
 * for each type we have in the code and test against those instead of predefined mock data. This style of testing can
 * catch corner case bugs and test algebraic properties of the code, for example deserialize(serialize(generatedThing)) == generatedThing
 *
 * TODO add combinators for easier Generator writing
 */
class ContractStateGenerator : Generator<ContractState>(ContractState::class.java) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): ContractState {
        return Cash.State(
                amount = AmountGenerator(IssuedGenerator(CurrencyGenerator())).generate(random, status),
                owner = AnonymousParty(PublicKeyGenerator().generate(random, status))
        )
    }
}

class MoveGenerator : Generator<Cash.Commands.Move>(Cash.Commands.Move::class.java) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): Cash.Commands.Move {
        return Cash.Commands.Move(SecureHashGenerator().generate(random, status))
    }
}

class IssueGenerator : Generator<Cash.Commands.Issue>(Cash.Commands.Issue::class.java) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): Cash.Commands.Issue {
        return Cash.Commands.Issue(random.nextLong())
    }
}

class ExitGenerator : Generator<Cash.Commands.Exit>(Cash.Commands.Exit::class.java) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): Cash.Commands.Exit {
        return Cash.Commands.Exit(AmountGenerator(IssuedGenerator(CurrencyGenerator())).generate(random, status))
    }
}

class CommandDataGenerator : Generator<CommandData>(CommandData::class.java) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): CommandData {
        val generators = listOf(MoveGenerator(), IssueGenerator(), ExitGenerator())
        return generators[random.nextInt(0, generators.size - 1)].generate(random, status)
    }
}

class CommandGenerator : Generator<Command>(Command::class.java) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): Command {
        val signersGenerator = ArrayListGenerator()
        signersGenerator.addComponentGenerators(listOf(PublicKeyGenerator()))
        return Command(CommandDataGenerator().generate(random, status), PublicKeyGenerator().generate(random, status))
    }
}

class WiredTransactionGenerator : Generator<WireTransaction>(WireTransaction::class.java) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): WireTransaction {
        val commands = CommandGenerator().generateList(random, status) + listOf(CommandGenerator().generate(random, status))
        return WireTransaction(
                inputs = StateRefGenerator().generateList(random, status),
                attachments = SecureHashGenerator().generateList(random, status),
                outputs = TransactionStateGenerator(ContractStateGenerator()).generateList(random, status),
                commands = commands,
                notary = PartyGenerator().generate(random, status),
                signers = commands.flatMap { it.signers },
                type = TransactionType.General,
                timeWindow = TimeWindowGenerator().generate(random, status)
        )
    }
}

class SignedTransactionGenerator : Generator<SignedTransaction>(SignedTransaction::class.java) {
    override fun generate(random: SourceOfRandomness, status: GenerationStatus): SignedTransaction {
        val wireTransaction = WiredTransactionGenerator().generate(random, status)
        return SignedTransaction(
                txBits = wireTransaction.serialized,
                sigs = listOf(NullSignature)
        )
    }
}
