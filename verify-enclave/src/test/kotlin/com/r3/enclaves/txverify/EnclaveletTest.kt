package com.r3.enclaves.txverify

import net.corda.contracts.asset.Cash
import net.corda.contracts.asset.DUMMY_CASH_ISSUER
import net.corda.core.contracts.POUNDS
import net.corda.core.contracts.`issued by`
import net.corda.core.crypto.random63BitValue
import net.corda.core.identity.AnonymousParty
import net.corda.core.serialization.serialize
import net.corda.testing.MEGA_CORP_PUBKEY
import net.corda.testing.MINI_CORP_PUBKEY
import net.corda.testing.ledger
import org.junit.Test
import java.nio.file.Paths
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EnclaveletTest {
    @Test
    fun success() {
        ledger {
            // Issue a couple of cash states and spend them.
            val wtx1 = transaction {
                output("c1", Cash.State(1000.POUNDS `issued by` DUMMY_CASH_ISSUER, AnonymousParty(MEGA_CORP_PUBKEY)))
                command(DUMMY_CASH_ISSUER.party.owningKey, Cash.Commands.Issue(random63BitValue()))
                verifies()
            }
            val wtx2 = transaction {
                output("c2", Cash.State(2000.POUNDS `issued by` DUMMY_CASH_ISSUER, AnonymousParty(MEGA_CORP_PUBKEY)))
                command(DUMMY_CASH_ISSUER.party.owningKey, Cash.Commands.Issue(random63BitValue()))
                verifies()
            }
            val wtx3 = transaction {
                input("c1")
                input("c2")
                output(Cash.State(3000.POUNDS `issued by` DUMMY_CASH_ISSUER, AnonymousParty(MINI_CORP_PUBKEY)))
                command(MEGA_CORP_PUBKEY, Cash.Commands.Move())
                verifies()
            }

            val req = TransactionVerificationRequest(wtx3.serialized, arrayOf(wtx1.serialized, wtx2.serialized), emptyArray())
            val serialized = req.serialize()
            serialized.writeToFile(Paths.get("/tmp/req"))
            verifyInEnclave(serialized.bytes)
        }
    }

    @Test
    fun fail() {
        ledger {
            // Issue a couple of cash states and spend them.
            val wtx1 = transaction {
                output("c1", Cash.State(1000.POUNDS `issued by` DUMMY_CASH_ISSUER, AnonymousParty(MEGA_CORP_PUBKEY)))
                command(DUMMY_CASH_ISSUER.party.owningKey, Cash.Commands.Issue(random63BitValue()))
                verifies()
            }
            val wtx2 = transaction {
                output("c2", Cash.State(2000.POUNDS `issued by` DUMMY_CASH_ISSUER, AnonymousParty(MEGA_CORP_PUBKEY)))
                command(DUMMY_CASH_ISSUER.party.owningKey, Cash.Commands.Issue(random63BitValue()))
                verifies()
            }
            val wtx3 = transaction {
                input("c1")
                input("c2")
                output(Cash.State(3000.POUNDS `issued by` DUMMY_CASH_ISSUER, AnonymousParty(MINI_CORP_PUBKEY)))
                failsWith("Required net.corda.core.contracts.FungibleAsset.Commands.Move command")
            }
            val req = TransactionVerificationRequest(wtx3.serialized, arrayOf(wtx1.serialized, wtx2.serialized), emptyArray())
            val e = assertFailsWith<Exception> { verifyInEnclave(req.serialize().bytes) }
            assertTrue(e.message!!.contains("Required net.corda.core.contracts.FungibleAsset.Commands.Move command"))
        }
    }
}