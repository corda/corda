package com.r3corda.contracts

import com.r3corda.contracts.asset.Cash
import com.r3corda.contracts.testing.CASH
import com.r3corda.contracts.testing.`issued by`
import com.r3corda.contracts.testing.`owned by`
import com.r3corda.contracts.testing.`with notary`
import com.r3corda.core.contracts.DOLLARS
import com.r3corda.core.contracts.LedgerTransaction
import com.r3corda.core.contracts.`issued by`
import com.r3corda.core.contracts.verifyToLedgerTransaction
import com.r3corda.core.node.services.testing.MockStorageService
import com.r3corda.core.seconds
import com.r3corda.core.serialization.OpaqueBytes
import com.r3corda.core.testing.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
//import java.util.*
//import kotlin.test.fail

/**
 * unit test cases that confirms the correct behavior of the AccountReceivable smart contract
 */
class AccountReceivableTests {
    val INVOICE_TIME = Instant.parse("2015-04-17T12:00:00.00Z")
    val PAST_INVOICE_TIME = Instant.parse("2014-04-17T12:00:00.00Z")

    val defaultRef = OpaqueBytes(ByteArray(1, { 1 }))
    val defaultIssuer = MEGA_CORP.ref(defaultRef)

    val notary = DUMMY_NOTARY

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
            invoiceDate = INVOICE_TIME.atZone(ZoneOffset.UTC).toLocalDate(),
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
    val initialInvoiceState = Invoice.State(MINI_CORP, ALICE, false,invoiceProperties)
    val initialAR = AccountReceivable.createARFromInvoice(initialInvoiceState, 0.9, notary)

    enum class WhatKind {
        PAST, FUTURE
    }

    fun generateInvoiceIssueTxn(kind: WhatKind = WhatKind.FUTURE): LedgerTransaction {
        val genTX: LedgerTransaction = run {
            val pastProp = initialInvoiceState.props.copy(invoiceDate =
                    PAST_INVOICE_TIME.atZone(ZoneOffset.UTC).toLocalDate())

            val invoice: Invoice.State = if (kind == WhatKind.PAST) {
                initialInvoiceState.copy(props = pastProp)
            } else {
                initialInvoiceState
            }

            val gtx = invoice.generateInvoice(DUMMY_NOTARY).apply {
                setTime(TEST_TX_TIME, DUMMY_NOTARY, 30.seconds)
                signWith(MINI_CORP_KEY)
                signWith(DUMMY_NOTARY_KEY)
            }
            gtx.toSignedTransaction().verifyToLedgerTransaction(MOCK_IDENTITY_SERVICE, MockStorageService().attachments)
        }
        return genTX
    }

    fun issuedInvoice(): Invoice.State {
        return generateInvoiceIssueTxn().outputs.filterIsInstance<Invoice.State>().single()
    }

    fun issuedInvoiceWithPastDate(): Invoice.State {
        return generateInvoiceIssueTxn(WhatKind.PAST).outputs.filterIsInstance<Invoice.State>().single()
    }

    @Test
    fun `Apply - requireThat Tests`() {
        //Happy Path Apply
        transaction {
            input() { issuedInvoice() }
            output { issuedInvoice().copy(assigned = true) }
            output { initialAR.data }
            arg(MINI_CORP_PUBKEY) { Invoice.Commands.Assign() }
            arg(MEGA_CORP_PUBKEY) { AccountReceivable.Commands.Apply() }
            timestamp(TEST_TX_TIME)
            accepts()
        }

        transaction {
            output { initialAR.data }
            arg(MEGA_CORP_PUBKEY) { AccountReceivable.Commands.Apply() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "Required com.r3corda.contracts.Invoice.Commands command"
        }

        transaction {
            output { initialAR.data }
            arg(MINI_CORP_PUBKEY) { Invoice.Commands.Assign() }
            arg(MEGA_CORP_PUBKEY) { AccountReceivable.Commands.Apply() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "There must be an input Invoice state"
        }

        transaction {
            input() { issuedInvoice() }
            output { initialInvoiceState.copy(assigned = true) }
            output { initialAR.data }
            arg(MINI_CORP_PUBKEY) { Invoice.Commands.Assign() }
            arg(MEGA_CORP_PUBKEY) { AccountReceivable.Commands.Apply() }
            this `fails requirement` "must be timestamped"
        }

        transaction {
            input() { issuedInvoice() }
            output { initialInvoiceState.copy(assigned = true) }
            output { initialAR.data.copy(status = AccountReceivable.StatusEnum.Issued) }
            arg(MEGA_CORP_PUBKEY) { AccountReceivable.Commands.Apply() }
            arg(MINI_CORP_PUBKEY) { Invoice.Commands.Assign() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "AR state must be applied"
        }

        transaction {
            input() { issuedInvoice() }
            output { initialInvoiceState.copy(assigned = true) }
            output { initialAR.data.copy(props = initialAR.data.props.copy(invoiceID = "BOB")) }
            arg(MEGA_CORP_PUBKEY) { AccountReceivable.Commands.Apply() }
            arg(MINI_CORP_PUBKEY) { Invoice.Commands.Assign() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "AR properties must match input invoice"
        }

        transaction {
            input() { issuedInvoiceWithPastDate() }
            output { issuedInvoiceWithPastDate().copy(assigned = true) }
            output { AccountReceivable.createARFromInvoice(
                    issuedInvoiceWithPastDate(), 0.9, notary).data }
            arg(MEGA_CORP_PUBKEY) { AccountReceivable.Commands.Apply() }
            arg(MINI_CORP_PUBKEY) { Invoice.Commands.Assign() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "the payment date must be in the future"
        }

        transaction {
            input() { issuedInvoice() }
            output { issuedInvoice().copy(assigned = true) }
            output { AccountReceivable.createARFromInvoice(
                    issuedInvoice(), 1.9, notary).data }
            arg(MEGA_CORP_PUBKEY) { AccountReceivable.Commands.Apply() }
            arg(MINI_CORP_PUBKEY) { Invoice.Commands.Assign() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "The discount factor is invalid"
        }
    }

    @Test
    fun `Issue - requireThat Tests`() {
        //Happy Path Apply
        transaction {
            input() { AccountReceivable.createARFromInvoice(
                    issuedInvoice(), 0.9, notary).data }
            output { AccountReceivable.createARFromInvoice(
                    issuedInvoice(), 0.9, notary).data.copy(status = AccountReceivable.StatusEnum.Issued) }
            arg(MEGA_CORP_PUBKEY) { AccountReceivable.Commands.Issue() }
            timestamp(TEST_TX_TIME)
            accepts()
        }

        transaction {
            input() { AccountReceivable.createARFromInvoice(
                    issuedInvoice(), 0.9, notary).data.copy(status = AccountReceivable.StatusEnum.Issued) }
            output { AccountReceivable.createARFromInvoice(
                    issuedInvoice(), 0.9, notary).data.copy(status = AccountReceivable.StatusEnum.Issued) }
            arg(MEGA_CORP_PUBKEY) { AccountReceivable.Commands.Issue() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "input status must be applied"
        }

        transaction {
            input() { AccountReceivable.createARFromInvoice(
                    issuedInvoice(), 0.9, notary).data.copy(status = AccountReceivable.StatusEnum.Applied) }
            output { AccountReceivable.createARFromInvoice(
                    issuedInvoice(), 0.9, notary).data.copy(status = AccountReceivable.StatusEnum.Applied) }
            arg(MEGA_CORP_PUBKEY) { AccountReceivable.Commands.Issue() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "output status must be issued"
        }

        transaction {
            input() { AccountReceivable.createARFromInvoice(
                    issuedInvoice(), 0.9, notary).data.copy(status = AccountReceivable.StatusEnum.Applied) }
            output { AccountReceivable.createARFromInvoice(
                    issuedInvoice(), 0.95, notary).data.copy(status = AccountReceivable.StatusEnum.Issued) }
            arg(MEGA_CORP_PUBKEY) { AccountReceivable.Commands.Issue() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "properties must match"
        }
    }

    @Test
    fun `Extinguish - requireThat Tests`() {
        //Happy Path Extinguish
        transaction {
            input() { AccountReceivable.createARFromInvoice(
                    issuedInvoiceWithPastDate(), 0.9, notary).data.copy(status = AccountReceivable.StatusEnum.Issued) }
            arg(MEGA_CORP_PUBKEY) { AccountReceivable.Commands.Extinguish() }
            timestamp(TEST_TX_TIME)
            accepts()
        }

        transaction {
            input() { AccountReceivable.createARFromInvoice(
                    issuedInvoice(), 0.9, notary).data.copy(status = AccountReceivable.StatusEnum.Issued) }
            arg(MEGA_CORP_PUBKEY) { AccountReceivable.Commands.Extinguish() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "the payment date must be today or in the the past"
        }

        transaction {
            input() { AccountReceivable.createARFromInvoice(
                    issuedInvoiceWithPastDate(), 0.9, notary).data.copy(status = AccountReceivable.StatusEnum.Applied) }
            arg(MEGA_CORP_PUBKEY) { AccountReceivable.Commands.Extinguish() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "input status must be issued"
        }

        transaction {
            input { AccountReceivable.createARFromInvoice(
                    issuedInvoiceWithPastDate(), 0.9, notary).data.copy(status = AccountReceivable.StatusEnum.Issued) }
            output { AccountReceivable.createARFromInvoice(
                    issuedInvoiceWithPastDate(), 0.9, notary).data.copy(status = AccountReceivable.StatusEnum.Issued) }
            arg(MEGA_CORP_PUBKEY) { AccountReceivable.Commands.Extinguish() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "output state must not exist"
        }
    }

    @Test
    fun ok() {
        createARAndSendToBank().verify()
    }

    val START_TIME = Instant.parse("2015-04-17T12:00:00.00Z")
    val APPLY_TIME = Instant.parse("2015-04-17T12:05:00.00Z")
    val ISSUE_TIME = Instant.parse("2015-04-17T12:15:00.00Z")
    val END_TIME = Instant.parse("2015-04-27T12:00:00.00Z")

    private fun createARAndSendToBank(): TransactionGroupDSL<AccountReceivable.State> {

        return transactionGroupFor {
            roots {
                transaction(99.DOLLARS.CASH `issued by` defaultIssuer`owned by` MEGA_CORP_PUBKEY  `with notary` DUMMY_NOTARY label "bank's money")
                transaction(110.DOLLARS.CASH `issued by` defaultIssuer `owned by` ALICE_PUBKEY  `with notary` DUMMY_NOTARY label "buyer's money")
            }

            val newProps = invoiceProperties.copy(invoiceDate = START_TIME.atZone(ZoneOffset.UTC).toLocalDate(),
                    term = 5)
            val newInvoice = initialInvoiceState.copy(props = newProps)
            val ar = AccountReceivable.createARFromInvoice(newInvoice, 0.90, notary)

            // 1. Create new invoice
            transaction {
                output("new invoice") { newInvoice }
                arg(MINI_CORP_PUBKEY) { Invoice.Commands.Issue() }
                timestamp(START_TIME)
            }

            // 2. create new AR
            transaction {
                input("new invoice")
                output("applied invoice") { initialInvoiceState.copy(assigned=true, props = newProps) }
                output("new AR") { ar.data }
                arg(MINI_CORP_PUBKEY) { Invoice.Commands.Assign() }
                arg(MEGA_CORP_PUBKEY) { AccountReceivable.Commands.Apply() }
                timestamp(APPLY_TIME)
            }

            // 3. issue AR
            transaction {
                input ("new AR")
                input("bank's money")
                output ("issued AR") {
                    ar.data.copy(status=AccountReceivable.StatusEnum.Issued)
                }
                output { 99.DOLLARS.CASH `owned by` MINI_CORP_PUBKEY }
                arg(MEGA_CORP_PUBKEY) { Cash.Commands.Move() }
                arg(MINI_CORP_PUBKEY) { AccountReceivable.Commands.Issue() }
                timestamp(ISSUE_TIME)
            }

            // 4. extinguish AR
            transaction {
                input ("applied invoice")
                input ("issued AR")
                input ("buyer's money")
                output { 110.DOLLARS.CASH `owned by` MEGA_CORP_PUBKEY }
                arg(ALICE_PUBKEY) { Cash.Commands.Move() }
                arg(MINI_CORP_PUBKEY) { Invoice.Commands.Extinguish() }
                arg(MEGA_CORP_PUBKEY) { AccountReceivable.Commands.Extinguish() }
                timestamp(END_TIME)
            }

        }
    }

}