package com.r3corda.contracts

import com.r3corda.core.contracts.DOLLARS
import com.r3corda.core.contracts.`issued by`
import com.r3corda.core.serialization.OpaqueBytes
import com.r3corda.core.testing.*
import org.junit.Test
import java.time.LocalDate
import java.util.*
import kotlin.test.fail

class InvoiceTests {

    val defaultRef = OpaqueBytes(ByteArray(1, { 1 }))
    val defaultIssuer = MEGA_CORP.ref(defaultRef)

    val invoiceProperties = Invoice.InvoiceProperties(
                            invoiceID = "123",
                            seller = LocDataStructures.Company(
                                    name = "Mega Corp LTD.",
                                    address = "123 Main St. Awesome Town, ZZ 11111",
                                    phone = null
                            ),
                            buyer = LocDataStructures.Company(
                                    name = "Sandworm Imports",
                                    address = "555 Elm St. Little Town, VV, 22222",
                                    phone = null
                            ),
                            invoiceDate = LocalDate.now(),
                            term = 60,
                            goods = arrayListOf<LocDataStructures.PricedGood>(
                                    LocDataStructures.PricedGood(
                                            description = "Salt",
                                            purchaseOrderRef = null,
                                            quantity = 10,
                                            unitPrice = 3.DOLLARS `issued by` defaultIssuer,
                                            grossWeight = null
                                    ),
                                    LocDataStructures.PricedGood(
                                            description = "Pepper",
                                            purchaseOrderRef = null,
                                            quantity = 20,
                                            unitPrice = 4.DOLLARS `issued by` defaultIssuer,
                                            grossWeight = null
                                    )
                            )
                    )
    val initialInvoiceState = Invoice.State(MEGA_CORP, ALICE, false,invoiceProperties)

    @Test
    fun `Issue - requireThat Tests`() {

        //Happy Path Issue
        transaction {
            output { initialInvoiceState }
            arg(MEGA_CORP_PUBKEY) { Invoice.Commands.Issue() }
            timestamp(TEST_TX_TIME)
            accepts()
        }

        transaction {
            input { initialInvoiceState }
            output { initialInvoiceState }
            arg(MEGA_CORP_PUBKEY) { Invoice.Commands.Issue() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "there is no input state"
        }

        transaction {
            output { initialInvoiceState }
            arg(DUMMY_PUBKEY_1) { Invoice.Commands.Issue() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "the transaction is signed by the invoice owner"
        }

        var props = invoiceProperties.copy(seller = invoiceProperties.buyer);
        transaction {
            output { initialInvoiceState.copy(props = props) }
            arg(MEGA_CORP_PUBKEY) { Invoice.Commands.Issue() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "the buyer and seller must be different"
        }

        transaction {
            output { initialInvoiceState.copy(assigned = true) }
            arg(MEGA_CORP_PUBKEY) { Invoice.Commands.Issue() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "the invoice must not be assigned"
        }

        props = invoiceProperties.copy(invoiceID = "");
        transaction {
            output { initialInvoiceState.copy(props = props) }
            arg(MEGA_CORP_PUBKEY) { Invoice.Commands.Issue() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "the invoice ID must not be blank"
        }

        val withMessage = "the term must be a positive number"
        val r = try {
            props = invoiceProperties.copy(term = 0);
            false
        } catch (e: Exception) {
            val m = e.message
            if (m == null)
                fail("Threw exception without a message")
            else
                if (!m.toLowerCase().contains(withMessage.toLowerCase())) throw AssertionError("Error was actually: $m", e)
            true
        }
        if (!r) throw AssertionError("Expected exception but didn't get one")

        props = invoiceProperties.copy(invoiceDate = LocalDate.now().minusDays(invoiceProperties.term + 1))
        transaction {
            output { initialInvoiceState.copy(props = props) }
            arg(MEGA_CORP_PUBKEY) { Invoice.Commands.Issue() }
            timestamp(java.time.Instant.now())
            this `fails requirement` "the payment date must be in the future"
        }

        val withMessage2 = "there must be goods assigned to the invoice"
        val r2 = try {
            props = invoiceProperties.copy(goods = Collections.emptyList())
            false
        } catch (e: Exception) {
            val m = e.message
            if (m == null)
                fail("Threw exception without a message")
            else
                if (!m.toLowerCase().contains(withMessage2.toLowerCase())) {
                    throw AssertionError("Error was actually: $m expected $withMessage2", e)
                }
            true
        }
        if (!r2) throw AssertionError("Expected exception but didn't get one")

       val goods = arrayListOf<LocDataStructures.PricedGood>( LocDataStructures.PricedGood(
                    description = "Salt",
                    purchaseOrderRef = null,
                    quantity = 10,
                    unitPrice = 0.DOLLARS `issued by` defaultIssuer,
                    grossWeight = null
                ))

        props = invoiceProperties.copy(goods = goods)
        transaction {
            output { initialInvoiceState.copy(props = props) }
            arg(MEGA_CORP_PUBKEY) { Invoice.Commands.Issue() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "the invoice amount must be non-zero"
        }
    }

    @Test
    fun `Assign - requireThat Tests`() {

        //Happy Path Assign
        transaction {
            input { initialInvoiceState }
            output { initialInvoiceState.copy(assigned = true) }
            arg(MEGA_CORP_PUBKEY) { Invoice.Commands.Assign() }
            timestamp(TEST_TX_TIME)
            accepts()
        }

        transaction {
            input { initialInvoiceState }
            output { initialInvoiceState.copy(owner = ALICE) }
            arg(MEGA_CORP_PUBKEY) { Invoice.Commands.Assign() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "input state owner must be the same as the output state owner"
        }

        transaction {
            input { initialInvoiceState }
            output { initialInvoiceState }
            arg(DUMMY_PUBKEY_1) { Invoice.Commands.Assign() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "the transaction must be signed by the owner"
        }

        var props = invoiceProperties.copy(seller = invoiceProperties.buyer);
        transaction {
            input { initialInvoiceState }
            output { initialInvoiceState.copy(props = props) }
            arg(MEGA_CORP_PUBKEY) { Invoice.Commands.Assign() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "the invoice properties must remain unchanged"
        }

        transaction {
            input { initialInvoiceState.copy(assigned = true) }
            output { initialInvoiceState }
            arg(MEGA_CORP_PUBKEY) { Invoice.Commands.Assign() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "the input invoice must not be assigned"
        }

        transaction {
            input { initialInvoiceState }
            output { initialInvoiceState.copy(assigned = false) }
            arg(MEGA_CORP_PUBKEY) { Invoice.Commands.Assign() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "the output invoice must be assigned"
        }

        props = invoiceProperties.copy(invoiceDate = LocalDate.now().minusDays(invoiceProperties.term + 1))
        transaction {
            input { initialInvoiceState.copy(props = props) }
            output { initialInvoiceState.copy(props = props, assigned = true) }
            arg(MEGA_CORP_PUBKEY) { Invoice.Commands.Assign() }
            timestamp(java.time.Instant.now())
            this `fails requirement` "the payment date must be in the future"
        }
    }

    @Test
    fun `Extinguish - requireThat Tests`() {

        //Happy Path Extinguish
        val props = invoiceProperties.copy(invoiceDate = LocalDate.now().minusDays(invoiceProperties.term + 1))
        transaction {
            input { initialInvoiceState.copy(props = props) }
            arg(MEGA_CORP_PUBKEY) { Invoice.Commands.Extinguish() }
            timestamp(java.time.Instant.now())
            accepts()
        }

        transaction {
            input { initialInvoiceState }
            output { initialInvoiceState }
            arg(MEGA_CORP_PUBKEY) { Invoice.Commands.Extinguish() }
            timestamp(java.time.Instant.now())
            this `fails requirement` "there shouldn't be an output state"
        }

        transaction {
            input { initialInvoiceState }
            arg(DUMMY_PUBKEY_1) { Invoice.Commands.Extinguish() }
            timestamp(java.time.Instant.now())
            this `fails requirement` "the transaction must be signed by the owner"
        }

//        transaction {
//            input { initialInvoiceState }
//            arg(MEGA_CORP_PUBKEY) { Invoice.Commands.Extinguish() }
//            timestamp(java.time.Instant.now())
//            this `fails requirement` "the payment date must be today or in the past"
//        }
    }
}