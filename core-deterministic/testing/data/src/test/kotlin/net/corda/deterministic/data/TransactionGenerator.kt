package net.corda.deterministic.data

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.serialization.serialize
import net.corda.deterministic.verifier.MockContractAttachment
import net.corda.deterministic.verifier.SampleCommandData
import net.corda.deterministic.verifier.TransactionVerificationRequest
import net.corda.finance.POUNDS
import net.corda.finance.`issued by`
import net.corda.finance.contracts.asset.Cash.*
import net.corda.finance.contracts.asset.Cash.Commands.*
import net.corda.finance.contracts.asset.Cash.Companion.PROGRAM_ID
import net.corda.node.services.api.IdentityServiceInternal
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.getTestPartyAndCertificate
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import java.io.OutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.PublicKey

object TransactionGenerator {
    private val DUMMY_NOTARY: Party = TestIdentity(DUMMY_NOTARY_NAME, 20).party

    private val DUMMY_CASH_ISSUER_KEY: KeyPair = entropyToKeyPair(BigInteger.valueOf(10))
    private val DUMMY_CASH_ISSUER_IDENTITY = getTestPartyAndCertificate(Party(CordaX500Name("Snake Oil Issuer", "London", "GB"), DUMMY_CASH_ISSUER_KEY.public))
    private val DUMMY_CASH_ISSUER = DUMMY_CASH_ISSUER_IDENTITY.party.ref(1)

    private val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
    private val MEGA_CORP: Party = megaCorp.party
    private val MEGA_CORP_PUBKEY: PublicKey = megaCorp.keyPair.public
    private val MINI_CORP_PUBKEY: PublicKey = TestIdentity(CordaX500Name("MiniCorp", "London", "GB")).keyPair.public

    private val ledgerServices = MockServices(emptyList(), MEGA_CORP.name, rigorousMock<IdentityServiceInternal>().also {
        doReturn(MEGA_CORP).whenever(it).partyFromKey(MEGA_CORP_PUBKEY)
        doReturn(DUMMY_CASH_ISSUER.party).whenever(it).partyFromKey(DUMMY_CASH_ISSUER_KEY.public)
    })

    fun writeSuccess(output: OutputStream) {
        ledgerServices.ledger(DUMMY_NOTARY) {
            // Issue a couple of cash states and spend them.
            val wtx1 = transaction {
                attachments(PROGRAM_ID)
                output(PROGRAM_ID, "c1", State(1000.POUNDS `issued by` DUMMY_CASH_ISSUER, AnonymousParty(MEGA_CORP_PUBKEY)))
                command(DUMMY_CASH_ISSUER.party.owningKey, Issue())
                verifies()
            }
            val wtx2 = transaction {
                attachments(PROGRAM_ID)
                output(PROGRAM_ID, "c2", State(2000.POUNDS `issued by` DUMMY_CASH_ISSUER, AnonymousParty(MEGA_CORP_PUBKEY)))
                command(DUMMY_CASH_ISSUER.party.owningKey, Issue())
                verifies()
            }
            val wtx3 = transaction {
                attachments(PROGRAM_ID)
                input("c1")
                input("c2")
                output(PROGRAM_ID, "c3", State(3000.POUNDS `issued by` DUMMY_CASH_ISSUER, AnonymousParty(MINI_CORP_PUBKEY)))
                command(MEGA_CORP_PUBKEY, Move())
                verifies()
            }
            val contractAttachment = MockContractAttachment(interpreter.services.cordappProvider.getContractAttachmentID(PROGRAM_ID)!!, PROGRAM_ID)
            TransactionVerificationRequest(
                    wtx3.serialize(),
                    arrayOf(wtx1.serialize(), wtx2.serialize()),
                    arrayOf(contractAttachment.serialize().bytes))
                .serialize()
                .writeTo(output)
        }
    }

    fun writeFailure(output: OutputStream) {
        ledgerServices.ledger(DUMMY_NOTARY) {
            // Issue a couple of cash states and spend them.
            val wtx1 = transaction {
                attachments(PROGRAM_ID)
                output(PROGRAM_ID, "c1", State(1000.POUNDS `issued by` DUMMY_CASH_ISSUER, AnonymousParty(MEGA_CORP_PUBKEY)))
                command(DUMMY_CASH_ISSUER.party.owningKey, Issue())
                verifies()
            }
            val wtx2 = transaction {
                attachments(PROGRAM_ID)
                output(PROGRAM_ID, "c2", State(2000.POUNDS `issued by` DUMMY_CASH_ISSUER, AnonymousParty(MEGA_CORP_PUBKEY)))
                command(DUMMY_CASH_ISSUER.party.owningKey, Issue())
                verifies()
            }
            val wtx3 = transaction {
                attachments(PROGRAM_ID)
                input("c1")
                input("c2")
                command(DUMMY_CASH_ISSUER.party.owningKey, SampleCommandData)
                output(PROGRAM_ID, "c3", State(3000.POUNDS `issued by` DUMMY_CASH_ISSUER, AnonymousParty(MINI_CORP_PUBKEY)))
                failsWith("Required ${Move::class.java.canonicalName} command")
            }
            val contractAttachment = MockContractAttachment(interpreter.services.cordappProvider.getContractAttachmentID(PROGRAM_ID)!!, PROGRAM_ID)
            TransactionVerificationRequest(
                    wtx3.serialize(),
                    arrayOf(wtx1.serialize(), wtx2.serialize()),
                    arrayOf(contractAttachment.serialize().bytes))
                .serialize()
                .writeTo(output)
        }
    }
}