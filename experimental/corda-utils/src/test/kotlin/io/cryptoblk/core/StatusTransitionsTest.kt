package io.cryptoblk.core

import net.corda.core.contracts.Contract
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test

private val ALICE_ID = TestIdentity(CordaX500Name.parse("L=London,O=Alice Ltd,OU=Trade,C=GB"))
private val BOB_ID = TestIdentity(CordaX500Name.parse("L=London,O=Bob Ltd,OU=Trade,C=GB"))
private val BIGCORP_ID = TestIdentity(CordaX500Name.parse("L=New York,O=Bigcorp Ltd,OU=Trade,C=US"))
private val ALICE = ALICE_ID.party
private val BOB = BOB_ID.party
private val BIG_CORP = BIGCORP_ID.party
private val ALICE_PUBKEY = ALICE_ID.publicKey
private val BOB_PUBKEY = BOB_ID.publicKey
private val BIG_CORP_PUBKEY = BIGCORP_ID.publicKey

private enum class PartyRole {
    Adder,
    MultiplierAndRandomiser,
    Randomiser
}

private class IntegerTestState(override val status: String) : StatusTrackingContractState<String, PartyRole> {
    override fun roleToParty(role: PartyRole): Party {
        return when (role) {
            PartyRole.Adder -> BIG_CORP
            PartyRole.MultiplierAndRandomiser -> BOB
            PartyRole.Randomiser -> ALICE
        }
    }

    override val participants: List<AbstractParty>
        get() = listOf(ALICE, BOB, BIG_CORP)
}

private sealed class Operations: TypeOnlyCommandData() {
    object Add1 : Operations()
    object Add10 : Operations()
    object Multiply2 : Operations()
    object Randomise : Operations()
    object Close : Operations()
    object AnotherCommand : Operations()
}

class TestIntegerContract: Contract {
    companion object {
        private val fsTransitions = StatusTransitions(IntegerTestState::class,
            Operations.Add1.txDef(PartyRole.Adder, null, listOf("1")),
            Operations.Add1.txDef(PartyRole.Adder, "1", listOf("2")),
            Operations.Add10.txDef(PartyRole.Adder, "1", listOf("11")),
            Operations.Multiply2.txDef(PartyRole.MultiplierAndRandomiser, "2", listOf("4")),
            Operations.Multiply2.txDef(PartyRole.MultiplierAndRandomiser, "11", listOf("22")),
            Operations.Randomise.txDef(PartyRole.Randomiser, "2", listOf("8", "9", "1", "11")),
            Operations.Randomise.txDef(PartyRole.Randomiser, "11", listOf("2", "11", "4")),
            Operations.Randomise.txDef(PartyRole.MultiplierAndRandomiser, "11", listOf("22")),
            Operations.Close.txDef(PartyRole.Randomiser, "9", listOf(null))
        )
    }

    override fun verify(tx: LedgerTransaction) {
        fsTransitions.verify(tx)
    }
}

private class TestOwnedIntegerState(override val status: String): StatusTrackingContractState<String, PartyRole> {
    override val participants: List<AbstractParty>
        get() = listOf(ALICE, BOB)

    override fun roleToParty(role: PartyRole): Party {
        return if (status == "0") ALICE else BOB
    }
}

class TestOwnedIntegerContract: Contract {
    companion object {
        private val fsTransitions = StatusTransitions(TestOwnedIntegerState::class,
            Operations.Add1.txDef(PartyRole.Adder, null, listOf("0")),
            Operations.Add1.txDef(PartyRole.Adder, "0", listOf("1")),
            Operations.Add1.txDef(PartyRole.Adder, "1", listOf("2")),
            Operations.Multiply2.txDef(PartyRole.MultiplierAndRandomiser, "10", listOf("20")),
            Operations.Multiply2.txDef(PartyRole.Adder, "10", listOf("20")) // bug for the test
        )
    }

    override fun verify(tx: LedgerTransaction) {
        fsTransitions.verify(tx)
    }
}

class StatusTransitionsTest {

    companion object {
        private val integerContract = TestIntegerContract::class.qualifiedName!!
        private val ownedIntegerContract = TestOwnedIntegerContract::class.qualifiedName!!
        private val ledgerServices = MockServices(ALICE_ID, BOB_ID, BIGCORP_ID)
    }

    @Test
    fun `basic correct cases`() {
        ledgerServices.ledger  {
            transaction {
                output(integerContract, IntegerTestState("1"))
                command(BIG_CORP_PUBKEY, Operations.Add1)

                verifies()
            }
            transaction {
                input(integerContract, IntegerTestState("1"))
                output(integerContract, IntegerTestState("2"))
                command(BIG_CORP_PUBKEY, Operations.Add1)

                verifies()
            }
            transaction {
                input(integerContract, IntegerTestState("2"))
                output(integerContract, IntegerTestState("9"))
                command(ALICE_PUBKEY, Operations.Randomise)

                verifies()
            }
            transaction {
                input(integerContract, IntegerTestState("9"))
                command(ALICE_PUBKEY, Operations.Close)

                verifies()
            }
        }
    }

    @Test
    fun `disallowed output`() {
        ledgerServices.ledger {
            transaction {
                input(integerContract, IntegerTestState("1"))
                output(integerContract, IntegerTestState("3"))
                command(BIG_CORP_PUBKEY, Operations.Add1)

                fails()
            }
        }
    }

    @Test
    fun `disallowed command`() {
        ledgerServices.ledger {
            transaction {
                input(integerContract, IntegerTestState("1"))
                output(integerContract, IntegerTestState("2"))
                command(BIG_CORP_PUBKEY, Operations.Multiply2)

                fails()
            }
        }
    }

    @Test
    fun `disallowed signer`() {
        ledgerServices.ledger {
            transaction {
                input(integerContract, IntegerTestState("1"))
                output(integerContract, IntegerTestState("2"))
                command(ALICE_PUBKEY, Operations.Add1)

                fails()
            }
        }
    }

    @Test
    fun `irrelevant commands fail`() {
        ledgerServices.ledger {
            transaction {
                output(integerContract, IntegerTestState("8"))
                command(ALICE_PUBKEY, Operations.AnotherCommand)

                failsWith("at least one Command relevant")
            }
        }
    }

    @Test
    fun `multiple relevant commands accepted`() {
        ledgerServices.ledger {
            transaction {
                input(integerContract, IntegerTestState("11"))
                output(integerContract, IntegerTestState("22"))
                command(BOB_PUBKEY, Operations.Randomise)
                command(BOB_PUBKEY, Operations.Multiply2)

                verifies()
            }
        }
    }

    @Test
    fun `multiple relevant commands failed`() {
        ledgerServices.ledger {
            transaction {
                input(integerContract, IntegerTestState("2"))
                output(integerContract, IntegerTestState("4"))
                command(BOB_PUBKEY, Operations.Randomise)
                command(BOB_PUBKEY, Operations.Multiply2)

                fails()
            }
        }
    }

    @Test
    fun `multiple inputs failed`() {
        ledgerServices.ledger {
            transaction {
                input(integerContract, IntegerTestState("1"))
                input(integerContract, IntegerTestState("2"))
                output(integerContract, IntegerTestState("11"))
                command(BIG_CORP_PUBKEY, Operations.Add10)

                fails()
            }
        }
    }

    @Test
    fun `multiple outputs failed`() {
        ledgerServices.ledger {
            transaction {
                input(integerContract, IntegerTestState("1"))
                output(integerContract, IntegerTestState("2"))
                output(integerContract, IntegerTestState("11"))
                command(BIG_CORP_PUBKEY, Operations.Add10)

                fails()
            }
        }
    }

    @Test
    fun `role change signer correct`() {
        ledgerServices.ledger {
            transaction {
                output(ownedIntegerContract, TestOwnedIntegerState("0"))
                command(ALICE_PUBKEY, Operations.Add1)

                verifies()
            }
            transaction {
                input(ownedIntegerContract, TestOwnedIntegerState("0"))
                output(ownedIntegerContract, TestOwnedIntegerState("1"))
                command(ALICE_PUBKEY, Operations.Add1)

                verifies()
            }
            transaction {
                input(ownedIntegerContract, TestOwnedIntegerState("1"))
                output(ownedIntegerContract, TestOwnedIntegerState("2"))
                command(ALICE_PUBKEY, Operations.Add1)

                fails()
            }
            transaction {
                input(ownedIntegerContract, TestOwnedIntegerState("1"))
                output(ownedIntegerContract, TestOwnedIntegerState("2"))
                command(BOB_PUBKEY, Operations.Add1)

                verifies()
            }
        }
    }

    @Test
    fun `multiple signers disallowed`() {
        ledgerServices.ledger {
            transaction {
                input(ownedIntegerContract, TestOwnedIntegerState("10"))
                output(ownedIntegerContract, TestOwnedIntegerState("20"))
                command(ALICE_PUBKEY, Operations.Multiply2)

                failsWith("Cannot have different signers")
            }
        }
    }

    @Test
    fun `spend disallowed`() {
        ledgerServices.ledger {
            transaction {
                input(integerContract, IntegerTestState("1"))
                command(ALICE_PUBKEY, Operations.Close)

                fails()
            }
        }
    }
}