/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.enclaves.verify

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import com.r3.enclaves.txverify.MockContractAttachment
import com.r3.enclaves.txverify.NativeSgxApi
import com.r3.enclaves.txverify.TransactionVerificationRequest
import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.serialization.serialize
import net.corda.finance.POUNDS
import net.corda.finance.`issued by`
import net.corda.finance.contracts.asset.Cash
import net.corda.node.services.api.IdentityServiceInternal
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.getTestPartyAndCertificate
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Ignore
import org.junit.Test
import java.math.BigInteger
import kotlin.test.assertNull

class NativeSgxApiTest {
    companion object {
        val enclavePath = "../sgx-jvm/jvm-enclave/enclave/build/cordaenclave.signed.so"
        val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party
        val DUMMY_CASH_ISSUER_KEY = entropyToKeyPair(BigInteger.valueOf(10))
        val DUMMY_CASH_ISSUER_IDENTITY = getTestPartyAndCertificate(Party(CordaX500Name("Snake Oil Issuer", "London", "GB"), DUMMY_CASH_ISSUER_KEY.public))
        val DUMMY_CASH_ISSUER = DUMMY_CASH_ISSUER_IDENTITY.party.ref(1)
        val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
        val MEGA_CORP get() = megaCorp.party
        val MEGA_CORP_PUBKEY get() = megaCorp.keyPair.public
        val MINI_CORP_PUBKEY = TestIdentity(CordaX500Name("MiniCorp", "London", "GB")).keyPair.public
    }

    private val ledgerServices = MockServices(emptyList(), NativeSgxApiTest.MEGA_CORP.name, rigorousMock<IdentityServiceInternal>().also {
        doReturn(NativeSgxApiTest.MEGA_CORP).whenever(it).partyFromKey(NativeSgxApiTest.MEGA_CORP_PUBKEY)
    })

    @Ignore("The SGX code is not part of the standard build yet")
    @Test
    fun `verification of valid transaction works`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
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