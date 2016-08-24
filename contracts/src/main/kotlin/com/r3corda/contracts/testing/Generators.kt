package com.r3corda.contracts.testing

import com.pholser.junit.quickcheck.generator.GenerationStatus
import com.pholser.junit.quickcheck.generator.Generator
import com.pholser.junit.quickcheck.generator.java.util.ArrayListGenerator
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.DigitalSignature
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.testing.*
import java.util.*

class ContractStateGenerator : Generator<ContractState>(ContractState::class.java) {
    override fun generate(random: com.pholser.junit.quickcheck.random.SourceOfRandomness, status: GenerationStatus): ContractState {
        return com.r3corda.contracts.asset.Cash.State(
                amount = AmountGenerator(IssuedGenerator(CurrencyGenerator())).generate(random, status),
                owner = PublicKeyGenerator().generate(random, status)
        )
    }
}

class MoveGenerator : Generator<com.r3corda.contracts.asset.Cash.Commands.Move>(com.r3corda.contracts.asset.Cash.Commands.Move::class.java) {
    override fun generate(random: com.pholser.junit.quickcheck.random.SourceOfRandomness, status: GenerationStatus): com.r3corda.contracts.asset.Cash.Commands.Move {
        // TODO generate null as well
        return com.r3corda.contracts.asset.Cash.Commands.Move(SecureHashGenerator().generate(random, status))
    }
}

class IssueGenerator : Generator<com.r3corda.contracts.asset.Cash.Commands.Issue>(com.r3corda.contracts.asset.Cash.Commands.Issue::class.java) {
    override fun generate(random: com.pholser.junit.quickcheck.random.SourceOfRandomness, status: GenerationStatus): com.r3corda.contracts.asset.Cash.Commands.Issue {
        return com.r3corda.contracts.asset.Cash.Commands.Issue(random.nextLong())
    }
}

class ExitGenerator : Generator<com.r3corda.contracts.asset.Cash.Commands.Exit>(com.r3corda.contracts.asset.Cash.Commands.Exit::class.java) {
    override fun generate(random: com.pholser.junit.quickcheck.random.SourceOfRandomness, status: GenerationStatus): com.r3corda.contracts.asset.Cash.Commands.Exit {
        return com.r3corda.contracts.asset.Cash.Commands.Exit(AmountGenerator(IssuedGenerator(CurrencyGenerator())).generate(random, status))
    }
}

class CommandDataGenerator : Generator<CommandData>(CommandData::class.java) {
    override fun generate(random: com.pholser.junit.quickcheck.random.SourceOfRandomness, status: GenerationStatus): CommandData {
        val generators = listOf(MoveGenerator(), IssueGenerator(), ExitGenerator())
        return generators[random.nextInt(0, generators.size - 1)].generate(random, status)
    }
}

class CommandGenerator : Generator<Command>(Command::class.java) {
    override fun generate(random: com.pholser.junit.quickcheck.random.SourceOfRandomness, status: GenerationStatus): Command {
        val signersGenerator = ArrayListGenerator()
        signersGenerator.addComponentGenerators(listOf(PublicKeyGenerator()))
        return Command(CommandDataGenerator().generate(random, status), PublicKeyGenerator().generate(random, status))
    }
}

class WiredTransactionGenerator: Generator<WireTransaction>(WireTransaction::class.java) {
    override fun generate(random: com.pholser.junit.quickcheck.random.SourceOfRandomness, status: GenerationStatus): WireTransaction {
        val inputsGenerator = ArrayListGenerator()
        inputsGenerator.addComponentGenerators(listOf(StateRefGenerator()))
        val attachmentsGenerator = ArrayListGenerator()
        attachmentsGenerator.addComponentGenerators(listOf(SecureHashGenerator()))
        val outputsGenerator = ArrayListGenerator()
        outputsGenerator.addComponentGenerators(listOf(TransactionStateGenerator(ContractStateGenerator())))
        val commandsGenerator = ArrayListGenerator()
        commandsGenerator.addComponentGenerators(listOf(CommandGenerator()))
        val commands = commandsGenerator.generate(random, status) as ArrayList<Command> + listOf(CommandGenerator().generate(random, status))
        return WireTransaction(
                inputs = inputsGenerator.generate(random, status) as ArrayList<StateRef>,
                attachments = attachmentsGenerator.generate(random, status) as ArrayList<SecureHash>,
                outputs = outputsGenerator.generate(random, status) as ArrayList<TransactionState<ContractState>>,
                commands = commands,
                notary = PartyGenerator().generate(random, status),
                signers = commands.flatMap { it.signers },
                type = TransactionType.General(),
                timestamp = TimestampGenerator().generate(random, status)
        )
    }
}

class SignedTransactionGenerator: Generator<SignedTransaction>(SignedTransaction::class.java) {
    override fun generate(random: com.pholser.junit.quickcheck.random.SourceOfRandomness, status: GenerationStatus): SignedTransaction {
        val wireTransaction = WiredTransactionGenerator().generate(random, status)
        return SignedTransaction(
                txBits = wireTransaction.serialized,
                sigs = wireTransaction.signers.map { DigitalSignature.WithKey(it, random.nextBytes(16)) }
        )
    }
}
