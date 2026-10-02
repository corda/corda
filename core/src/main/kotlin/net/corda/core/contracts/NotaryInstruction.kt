package net.corda.core.contracts

import net.corda.core.DoNotImplement
import net.corda.core.serialization.CordaSerializable

/**
 * An additional instruction or directive for the notary to perform when it notarises the transaction. Each instruction implementation will
 * be understood by one or more notary types, and they are trusted to faithfully action them.
 *
 * For security and backwards compatibility, the default notary behaviour is to reject transactions with _any_ notary instruction. Therefore
 * only specialised notaries will support them. It's also possible for these notaries to reject transactions which have mixed instructions
 * and demand only their instructions be present.
 *
 * Note, notary instructions are not a notary plugin mechanism. All R3 notaries will reject any transaction with an unknown instruction.
 */
@DoNotImplement
@CordaSerializable
interface NotaryInstruction
