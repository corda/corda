package com.r3corda.contracts.testing

import com.pholser.junit.quickcheck.generator.GenerationStatus
import com.pholser.junit.quickcheck.generator.Generator
import com.pholser.junit.quickcheck.generator.java.util.ArrayListGenerator
import com.pholser.junit.quickcheck.random.SourceOfRandomness
import com.r3corda.contracts.asset.Cash
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.DigitalSignature
import com.r3corda.core.crypto.NullSignature
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.testing.*
import java.util.*

/**
 * This file contains generators for quickcheck style testing. The idea is that we can write random instance generators
 * for each type we have in the code and test against those instead of predefined mock data. This style of testing can
 * catch corner case bugs and test algebraic properties of the code, for example deserialize(serialize(generatedThing)) == generatedThing
 */
val contractStateGenerator = generator { random, status ->
    Cash.State(
            amount = amountGenerator(issuedGenerator(currencyGenerator)).generate(random, status),
            owner = publicKeyGenerator.generate(random, status)
    )
}

val moveGenerator = generator { random, status ->
    Cash.Commands.Move(secureHashGenerator.generate(random, status))
}

val issueGenerator = generator { random, status ->
    Cash.Commands.Issue(random.nextLong())
}

val exitGenerator = generator { random, status ->
    Cash.Commands.Exit(amountGenerator(issuedGenerator(currencyGenerator)).generate(random, status))
}

val commandDataGenerator = generator { random, status ->
    val generators = listOf(moveGenerator, issueGenerator, exitGenerator)
    generators[random.nextInt(0, generators.size - 1)].generate(random, status)
}

val commandGenerator = generator { random, status ->
    val signersGenerator = ArrayListGenerator()
    signersGenerator.addComponentGenerators(listOf(publicKeyGenerator))
    Command(commandDataGenerator.generate(random, status), publicKeyGenerator.generate(random, status))
}

val wiredTransactionGenerator = generator { random, status ->
    val commands = commandGenerator.generateList(random, status) + listOf(commandGenerator.generate(random, status))
    WireTransaction(
            inputs = stateRefGenerator.generateList(random, status),
            attachments = secureHashGenerator.generateList(random, status),
            outputs = transactionStateGenerator(contractStateGenerator).generateList(random, status),
            commands = commands,
            notary = partyGenerator.generate(random, status),
            signers = commands.flatMap { it.signers },
            type = TransactionType.General(),
            timestamp = timestampGenerator.generate(random, status)
    )
}

val signedTransactionGenerator = generator { random, status ->
    val wireTransaction = wiredTransactionGenerator.generate(random, status)
    SignedTransaction(
            txBits = wireTransaction.serialized,
            sigs = listOf(NullSignature)
    )
}
