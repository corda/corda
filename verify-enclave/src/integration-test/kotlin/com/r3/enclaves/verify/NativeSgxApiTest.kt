package com.r3.enclaves.verify

import com.r3.enclaves.txverify.MockContractAttachment
import com.r3.enclaves.txverify.NativeSgxApi
import com.r3.enclaves.txverify.TransactionVerificationRequest
import net.corda.core.identity.AnonymousParty
import net.corda.core.serialization.serialize
import net.corda.finance.POUNDS
import net.corda.finance.`issued by`
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.asset.DUMMY_CASH_ISSUER
import net.corda.testing.MEGA_CORP_PUBKEY
import net.corda.testing.MINI_CORP_PUBKEY
import net.corda.testing.ledger
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertNull

class NativeSgxApiTest {

    companion object {
        val enclavePath = "../sgx-jvm/jvm-enclave/enclave/build/cordaenclave.signed.so"
    }

    @Ignore("The SGX code is not part of the standard build yet")
    @Test
    fun `verification of valid transaction works`() {
        ledger {
            // Issue a couple of cash states and spend them.
            val wtx1 = transaction {
                attachments(Cash.PROGRAM_ID)
                output(Cash.PROGRAM_ID, "c1", Cash.State(1000.POUNDS `issued by` DUMMY_CASH_ISSUER, AnonymousParty(MEGA_CORP_PUBKEY)))
                command(DUMMY_CASH_ISSUER.party.owningKey, Cash.Commands.Issue())
                verifies()
            }
            val wtx2 = transaction {
                attachments(Cash.PROGRAM_ID)
                output(Cash.PROGRAM_ID, "c2", Cash.State(2000.POUNDS `issued by` DUMMY_CASH_ISSUER, AnonymousParty(MEGA_CORP_PUBKEY)))
                command(DUMMY_CASH_ISSUER.party.owningKey, Cash.Commands.Issue())
                verifies()
            }
            val wtx3 = transaction {
                attachments(Cash.PROGRAM_ID)
                input("c1")
                input("c2")
                output(Cash.PROGRAM_ID, "c3", Cash.State(3000.POUNDS `issued by` DUMMY_CASH_ISSUER, AnonymousParty(MINI_CORP_PUBKEY)))
                command(MEGA_CORP_PUBKEY, Cash.Commands.Move())
                verifies()
            }
            val cashContract = MockContractAttachment(interpreter.services.cordappProvider.getContractAttachmentID(Cash.PROGRAM_ID)!!, Cash.PROGRAM_ID)
            val req = TransactionVerificationRequest(wtx3.serialize(), arrayOf(wtx1.serialize(), wtx2.serialize()), arrayOf(cashContract.serialize().bytes))
            val serialized = req.serialize()
            assertNull(NativeSgxApi.verify(enclavePath, serialized.bytes))
        }
    }
}