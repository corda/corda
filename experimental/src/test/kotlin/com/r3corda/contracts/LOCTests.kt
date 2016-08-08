package com.r3corda.contracts

import com.r3corda.contracts.asset.Cash
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.node.services.testing.MockServices
import com.r3corda.core.serialization.OpaqueBytes
import com.r3corda.core.testing.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.Period


class LOCTests {

    val defaultRef = OpaqueBytes(ByteArray(1, { 1 }))
    val defaultIssuer = MEGA_CORP.ref(defaultRef)

    val pros = LOC.LOCProperties(
            letterOfCreditID = "letterOfCreditID",
            applicationDate = LocalDate.of(2016,5,15),
            issueDate =  LocalDate.now().minusDays(30),
            typeCredit = LocDataStructures.CreditType.SIGHT,
            amount = 100000.DOLLARS `issued by` defaultIssuer,
            expiryDate = LocalDate.now().plusDays(1),
            portLoading = LocDataStructures.Port("SG","Singapore",null,null,null),
            portDischarge = LocDataStructures.Port("US","Oakland",null,null,null),
            descriptionGoods = listOf(LocDataStructures.PricedGood(description="Tiger balm",
                    quantity = 10000,
                    grossWeight = null,
                    unitPrice = 1.DOLLARS `issued by` defaultIssuer,
                    purchaseOrderRef = null
            )),
            placePresentation = LocDataStructures.Location("US","California","Oakland"),
            latestShip = LocalDate.of(2016,6,12),
            periodPresentation = Period.ofDays(31),
            beneficiary = ALICE,
            issuingbank = MEGA_CORP,
            appplicant = CHARLIE,
            invoiceRef = StateRef(SecureHash.randomSHA256(),0)
    )

    val LOCstate = LOC.State(
            beneficiaryPaid = false,
            issued = false,
            terminated = false,
            props =pros
    )


    val Billpros = BillOfLadingAgreement.BillOfLadingProperties(
            billOfLadingID = "billOfLadingID",
            issueDate = LocalDate.of(2016,6,1),
            carrierOwner = BOB,
            nameOfVessel = "Karaboudjan",
            descriptionOfGoods = listOf(LocDataStructures.Good(description="Crab meet cans",quantity = 10000,grossWeight = null)),
            dateOfShipment = null,
            portOfLoading = LocDataStructures.Port(country = "Morokko",city = "Larache",address = null,name = null,state = null),
            portOfDischarge = LocDataStructures.Port(country = "Belgium",city = "Antwerpen",address = null, name = null, state = null),
            shipper = null,
            notify = LocDataStructures.Person(
                    name = "Some guy",
                    address = "Some address",
                    phone = "+11 23456789"
            ),
            consignee = LocDataStructures.Company(
                    name = "Some company",
                    address = "Some other address",
                    phone = "+11 12345678"
            ),
            grossWeight = LocDataStructures.Weight(
                    quantity =  2500.0,
                    unit = LocDataStructures.WeightUnit.KG
            )
    )

    val Billstate = BillOfLadingAgreement.State(
            owner = ALICE_PUBKEY,
            beneficiary = ALICE,
            props =Billpros
    )

    val Cashstate = Cash.State(
            deposit = MEGA_CORP.ref(1),
            amount = 100000.DOLLARS,
            owner = MEGA_CORP_PUBKEY
    )

    val invoiceState = Invoice.State(
            owner = ALICE,
            buyer = BOB,
            assigned = true,
            props = Invoice.InvoiceProperties(
                    invoiceID = "test",
                    seller = LocDataStructures.Company(
                            name = "Alice",
                            address = "",
                            phone = null
                    ),
                    buyer = LocDataStructures.Company(
                            name = "Charlie",
                            address = "",
                            phone = null
                    ),
                    invoiceDate = LocalDate.now().minusDays(1),
                    term = 1,
                    goods = listOf(LocDataStructures.PricedGood(
                            description = "Test good",
                            purchaseOrderRef = null,
                            quantity = 1000,
                            unitPrice = 100.DOLLARS `issued by` defaultIssuer,
                            grossWeight = null)
                    )
            )
    )

    lateinit var services: MockServices

    @Before
    fun setup() {
        services = MockServices()
    }

    @Test
    fun issueSignedByBank() {
        val ptx = LOC().generateIssue(LOCstate.beneficiaryPaid, true, LOCstate.terminated, LOCstate.props, DUMMY_NOTARY).apply {
            signWith(MEGA_CORP_KEY)
            signWith(DUMMY_NOTARY_KEY)
        }
        ptx.toSignedTransaction().toLedgerTransaction(services).verify()
    }

    @Test(expected = IllegalStateException::class)
    fun issueUnsigned() {
        val ptx = LOC().generateIssue(LOCstate.beneficiaryPaid, LOCstate.issued, LOCstate.terminated, LOCstate.props, DUMMY_NOTARY)
        ptx.toSignedTransaction().toLedgerTransaction(services).verify()
    }

    @Test(expected = IllegalStateException::class)
    fun issueKeyMismatch() {
        val ptx = LOC().generateIssue(LOCstate.beneficiaryPaid, LOCstate.issued, LOCstate.terminated, LOCstate.props, DUMMY_NOTARY).apply {
            signWith(BOB_KEY)
        }
        ptx.toSignedTransaction().toLedgerTransaction(services).verify()
    }


    @Test
    fun issueStatusTests() {

        transaction {
            output { LOCstate.copy(issued = false) }
            command(MEGA_CORP_PUBKEY) { LOC.Commands.Issuance() }
            timestamp(Instant.now())
            this.`fails with`("the LOC must be Issued");
        }
        transaction {
            output { LOCstate.copy(beneficiaryPaid = true, issued = true) }
            command(MEGA_CORP_PUBKEY) { LOC.Commands.Issuance() }
            timestamp(Instant.now())
            this.`fails with`("Demand Presentation must not be preformed successfully");
        }
        transaction {
            output { LOCstate.copy(terminated = true, issued = true) }
            command(MEGA_CORP_PUBKEY) { LOC.Commands.Issuance() }
            timestamp(Instant.now())
            this.`fails with`("LOC must not be terminated");
        }
        transaction {
            output { LOCstate.copy(issued = true) }
            command(MEGA_CORP_PUBKEY) { LOC.Commands.Issuance() }
            timestamp(Instant.now())
            this.verifies()
        }
        transaction {
            output { LOCstate.copy(issued = true, props = pros.copy(periodPresentation = Period.ofDays(0))) }
           // output { LOCstate.copy() }
            command(MEGA_CORP_PUBKEY) { LOC.Commands.Issuance() }
            timestamp(Instant.now())
            this.`fails with`("the period of presentation must be a positive number");
        }

    }

    @Test
    fun demandPresentaionTests() {
        transaction {
            input { LOCstate.copy(issued = true) }
            input { Billstate }
            input { Cashstate }
            input { invoiceState }
            output { LOCstate.copy(beneficiaryPaid = true, issued = true)}
            output { Billstate.copy(beneficiary = CHARLIE)}
            output { Cashstate.copy(owner = ALICE_PUBKEY) }
            command(MEGA_CORP_PUBKEY, ALICE_PUBKEY) { LOC.Commands.DemandPresentation() }
            command(ALICE_PUBKEY) { Invoice.Commands.Extinguish()}
            command(ALICE_PUBKEY) { BillOfLadingAgreement.Commands.TransferAndEndorseBL() }
            command(MEGA_CORP_PUBKEY) {Cash.Commands.Move()}
            timestamp(Instant.now())
            this.verifies();
        }

        transaction {
            input { LOCstate.copy(issued = true) }
            input { Billstate }
            input { Cashstate }
            input { invoiceState }
            output { LOCstate.copy(beneficiaryPaid = true, issued = true)}
            output { Billstate.copy(beneficiary = CHARLIE)}
            output { Cashstate.copy(owner = ALICE_PUBKEY) }
            command(ALICE_PUBKEY) { LOC.Commands.DemandPresentation() }
            command(ALICE_PUBKEY) { BillOfLadingAgreement.Commands.TransferAndEndorseBL() }
            command(ALICE_PUBKEY) { Invoice.Commands.Extinguish()}
            command(MEGA_CORP_PUBKEY) {Cash.Commands.Move()}
            timestamp(Instant.now())
            this.`fails with`("the transaction is signed by the issuing bank");
        }

        transaction {
            input { LOCstate.copy(issued = true) }
            input { Billstate }
            input { Cashstate }
            input { invoiceState }
            output { LOCstate.copy(beneficiaryPaid = true, issued = true)}
            output { Billstate.copy(beneficiary = CHARLIE)}
            output { Cashstate.copy(owner = ALICE_PUBKEY) }
            command(MEGA_CORP_PUBKEY) { LOC.Commands.DemandPresentation() }
            command(ALICE_PUBKEY) { Invoice.Commands.Extinguish()}
            command(ALICE_PUBKEY) { BillOfLadingAgreement.Commands.TransferAndEndorseBL() }
            command(MEGA_CORP_PUBKEY) {Cash.Commands.Move()}
            timestamp(Instant.now())
            this.`fails with`("the transaction is signed by the Beneficiary");
        }

        transaction {
            input { LOCstate.copy(issued = true) }
            input { Billstate }
            input { Cashstate }
            input { invoiceState }
            output { LOCstate.copy(beneficiaryPaid = true, issued = true, props = pros.copy(amount = 1.POUNDS `issued by` defaultIssuer ))}
            output { Billstate.copy(owner = CHARLIE_PUBKEY)}
            output { Cashstate.copy(owner = ALICE_PUBKEY) }
            command(MEGA_CORP_PUBKEY, ALICE_PUBKEY) { LOC.Commands.DemandPresentation() }
            command(ALICE_PUBKEY) { Invoice.Commands.Extinguish()}
            command(ALICE_PUBKEY) { BillOfLadingAgreement.Commands.TransferAndEndorseBL() }
            command(MEGA_CORP_PUBKEY) {Cash.Commands.Move()}
            timestamp(Instant.now())
            this.`fails with`("the LOC properties do not remain the same");
        }

        transaction {
            input { LOCstate.copy(issued = true, props = pros.copy(latestShip = Billstate.props.issueDate.minusDays(1))) }
            input { Billstate }
            input { Cashstate }
            input { invoiceState }
            output { LOCstate.copy(beneficiaryPaid = true, issued = true, props = pros.copy(latestShip = Billstate.props.issueDate.minusDays(1)))}
            output { Billstate.copy(beneficiary = CHARLIE)}
            output { Cashstate.copy(owner = ALICE_PUBKEY) }
            command(MEGA_CORP_PUBKEY, ALICE_PUBKEY) { LOC.Commands.DemandPresentation() }
            command(ALICE_PUBKEY) { Invoice.Commands.Extinguish()}
            command(ALICE_PUBKEY) { BillOfLadingAgreement.Commands.TransferAndEndorseBL() }
            command(MEGA_CORP_PUBKEY) {Cash.Commands.Move()}
            timestamp(Instant.now())
            this.`fails with`("the shipment is late");
        }

        transaction {
            input { LOCstate.copy(issued = true) }
            input { Billstate }
            input { Cashstate.copy(amount = 99000.DOLLARS `issued by` defaultIssuer) }
            input { invoiceState }
            output { LOCstate.copy(beneficiaryPaid = true, issued = true)}
            output { Billstate.copy(owner = CHARLIE_PUBKEY)}
            output { Cashstate.copy(amount = 99000.DOLLARS `issued by` defaultIssuer ).copy(owner = ALICE_PUBKEY) }
            command(MEGA_CORP_PUBKEY, ALICE_PUBKEY) { LOC.Commands.DemandPresentation() }
            command(ALICE_PUBKEY) { Invoice.Commands.Extinguish()}
            command(ALICE_PUBKEY) { BillOfLadingAgreement.Commands.TransferAndEndorseBL() }
            command(MEGA_CORP_PUBKEY) {Cash.Commands.Move()}
            timestamp(Instant.now())
            this.`fails with`("the cash state has not been transferred");
        }

        transaction {
            input { LOCstate.copy(issued = true) }
            input { Billstate }
            input { Cashstate }
            input { invoiceState }
            output { LOCstate.copy(beneficiaryPaid = true, issued = true)}
            output { Billstate.copy(beneficiary = ALICE)}
            output { Cashstate.copy(owner = ALICE_PUBKEY) }
            command(MEGA_CORP_PUBKEY, ALICE_PUBKEY) { LOC.Commands.DemandPresentation() }
            command(ALICE_PUBKEY) { Invoice.Commands.Extinguish()}
            command(ALICE_PUBKEY) { BillOfLadingAgreement.Commands.TransferAndEndorseBL() }
            command(MEGA_CORP_PUBKEY) {Cash.Commands.Move()}
            timestamp(Instant.now())
            this.`fails with`("the bill of lading has not been transferred");
        }

       /* transaction {
            input { LOCstate.copy(issued = true, props = pros.copy(issueDate = LocalDate.now().minusDays(32))) }
            input { Billstate }
            input { Cashstate }
            input { invoiceState }
            output { LOCstate.copy(beneficiaryPaid = true, issued = true, props = pros.copy(issueDate =LocalDate.now().minusDays(32)))}
            output { Billstate.copy(beneficiary = CHARLIE)}
            output { Cashstate.copy(owner = ALICE_PUBKEY) }
            command(MEGA_CORP_PUBKEY, ALICE_PUBKEY) { LOC.Commands.DemandPresentation() }
            command(ALICE_PUBKEY) { BillOfLadingAgreement.Commands.TransferAndEndorseBL() }
            command(ALICE_PUBKEY) { Invoice.Commands.Extinguish()}
            command(MEGA_CORP_PUBKEY) {Cash.Commands.Move()}
            timestamp(Instant.now())
            this.`fails requirement`("the presentation is late");
        }*/

        transaction {
            input { LOCstate.copy(issued = true) }
            input { Billstate }
            input { Cashstate }
            input { invoiceState }
            output { LOCstate.copy(beneficiaryPaid = false, issued = true)}
            output { Billstate.copy(beneficiary = CHARLIE)}
            output { Cashstate.copy(owner = ALICE_PUBKEY) }
            command(MEGA_CORP_PUBKEY, ALICE_PUBKEY) { LOC.Commands.DemandPresentation() }
            command(ALICE_PUBKEY) { BillOfLadingAgreement.Commands.TransferAndEndorseBL() }
            command(ALICE_PUBKEY) { Invoice.Commands.Extinguish()}
            command(MEGA_CORP_PUBKEY) {Cash.Commands.Move()}
            timestamp(Instant.now())
            this.`fails with`("the beneficiary has not been paid, status not changed");
        }

        transaction {
            input { LOCstate.copy(issued = false) }
            input { Billstate }
            input { Cashstate }
            input { invoiceState }
            output { LOCstate.copy(beneficiaryPaid = true, issued = false)}
            output { Billstate.copy(beneficiary = CHARLIE)}
            output { Cashstate.copy(owner = ALICE_PUBKEY) }
            command(MEGA_CORP_PUBKEY, ALICE_PUBKEY) { LOC.Commands.DemandPresentation() }
            command(ALICE_PUBKEY) { BillOfLadingAgreement.Commands.TransferAndEndorseBL() }
            command(ALICE_PUBKEY) { Invoice.Commands.Extinguish()}
            command(MEGA_CORP_PUBKEY) {Cash.Commands.Move()}
            timestamp(Instant.now())
            this.`fails with`("the LOC must be Issued");
        }

        transaction {
            input { LOCstate.copy(issued = true) }
            input { Billstate }
            input { Cashstate }
            input { invoiceState }
            output { LOCstate.copy(beneficiaryPaid = true, issued = true, terminated = true)}
            output { Billstate.copy(beneficiary = CHARLIE)}
            output { Cashstate.copy(owner = ALICE_PUBKEY) }
            command(MEGA_CORP_PUBKEY, ALICE_PUBKEY) { LOC.Commands.DemandPresentation() }
            command(ALICE_PUBKEY) { BillOfLadingAgreement.Commands.TransferAndEndorseBL() }
            command(ALICE_PUBKEY) { Invoice.Commands.Extinguish()}
            command(MEGA_CORP_PUBKEY) {Cash.Commands.Move()}
            timestamp(Instant.now())
            this.`fails with`("LOC must not be terminated");
        }

    }

    @Test
    fun terminationTests() {

        transaction {
            input { LOCstate.copy(issued = true, beneficiaryPaid = true) }
            input { Cashstate.copy(owner = CHARLIE_PUBKEY) }
            output { LOCstate.copy(beneficiaryPaid = true, issued = true, terminated = true)}
            output { Cashstate.copy(owner = MEGA_CORP_PUBKEY) }
            command(MEGA_CORP_PUBKEY, CHARLIE_PUBKEY) { LOC.Commands.Termination() }
            command(CHARLIE_PUBKEY) {Cash.Commands.Move()}
            timestamp(Instant.now())
            this.verifies();
        }
        transaction {
            input { LOCstate.copy(issued = true, beneficiaryPaid = true) }
            input { Cashstate.copy(owner = CHARLIE_PUBKEY) }
            output { LOCstate.copy(beneficiaryPaid = true, issued = true, terminated = true)}
            output { Cashstate.copy(owner = MEGA_CORP_PUBKEY) }
            command(ALICE_PUBKEY, CHARLIE_PUBKEY) { LOC.Commands.Termination() }
            command(CHARLIE_PUBKEY) {Cash.Commands.Move()}
            timestamp(Instant.now())
            this.`fails with`("the transaction is signed by the issuing bank");
        }

        /*transaction {
            input { LOCstate.copy(issued = true, beneficiaryPaid = true) }
            input { Cashstate.copy(owner = CHARLIE_PUBKEY) }
            output { LOCstate.copy(beneficiaryPaid = true, issued = true, terminated = true)}
            output { Cashstate.copy(owner = MEGA_CORP_PUBKEY) }
            command(MEGA_CORP_PUBKEY, ALICE_PUBKEY) { LOC.Commands.Termination() }
            command(CHARLIE_PUBKEY) {Cash.Commands.Move()}
            timestamp(Instant.now())
            this.`fails requirement`("the transaction is signed by the applicant");
        }*/

        transaction {
            input { LOCstate.copy(issued = true, beneficiaryPaid = true) }
            input { Cashstate.copy(owner = CHARLIE_PUBKEY) }
            output { LOCstate.copy(beneficiaryPaid = true, issued = true, terminated = true)}
            output { Cashstate.copy(amount = Cashstate.amount.minus(Amount(10,Cashstate.amount.token))) }
            command(MEGA_CORP_PUBKEY, CHARLIE_PUBKEY) { LOC.Commands.Termination() }
            command(CHARLIE_PUBKEY) {Cash.Commands.Move()}
            timestamp(Instant.now())
            this.`fails with`("the cash state has not been transferred");
        }

        transaction {
            input { LOCstate.copy(issued = true, beneficiaryPaid = true) }
            input { Cashstate.copy(owner = CHARLIE_PUBKEY) }
            output { LOCstate.copy(beneficiaryPaid = true, issued = true, terminated = true)}
            output { Cashstate.copy(owner = CHARLIE_PUBKEY) }
            command(MEGA_CORP_PUBKEY, CHARLIE_PUBKEY) { LOC.Commands.Termination() }
            command(CHARLIE_PUBKEY) {Cash.Commands.Move()}
            timestamp(Instant.now())
            this.`fails with`("Empty collection can't be reduced");
        }

        transaction {
            input { LOCstate.copy(issued = true, beneficiaryPaid = false) }
            input { Cashstate.copy(owner = CHARLIE_PUBKEY) }
            output { LOCstate.copy(beneficiaryPaid = false, issued = true, terminated = true)}
            output { Cashstate.copy(owner = MEGA_CORP_PUBKEY) }
            command(MEGA_CORP_PUBKEY, CHARLIE_PUBKEY) { LOC.Commands.Termination() }
            command(CHARLIE_PUBKEY) {Cash.Commands.Move()}
            timestamp(Instant.now())
            this.`fails with`("the beneficiary has not been paid, status not changed");
        }

        transaction {
            input { LOCstate.copy(issued = false, beneficiaryPaid = true) }
            input { Cashstate.copy(owner = CHARLIE_PUBKEY) }
            output { LOCstate.copy(beneficiaryPaid = true, issued = false, terminated = true)}
            output { Cashstate.copy(owner = MEGA_CORP_PUBKEY) }
            command(MEGA_CORP_PUBKEY, CHARLIE_PUBKEY) { LOC.Commands.Termination() }
            command(CHARLIE_PUBKEY) {Cash.Commands.Move()}
            timestamp(Instant.now())
            this.`fails with`("the LOC must be Issued");
        }
        transaction {
            input { LOCstate.copy(issued = true, beneficiaryPaid = true) }
            input { Cashstate.copy(owner = CHARLIE_PUBKEY) }
            output { LOCstate.copy(beneficiaryPaid = true, issued = true, terminated = false)}
            output { Cashstate.copy(owner = MEGA_CORP_PUBKEY) }
            command(MEGA_CORP_PUBKEY, CHARLIE_PUBKEY) { LOC.Commands.Termination() }
            command(CHARLIE_PUBKEY) {Cash.Commands.Move()}
            timestamp(Instant.now())
            this.`fails with`("LOC should be terminated");
        }

        transaction {
            input { LOCstate.copy(issued = true, beneficiaryPaid = true, props = pros.copy(amount = 1.POUNDS `issued by` defaultIssuer)) }
            input { Cashstate.copy(owner = CHARLIE_PUBKEY) }
            output { LOCstate.copy(beneficiaryPaid = true, issued = true, terminated = true)}
            output { Cashstate.copy(owner = MEGA_CORP_PUBKEY) }
            command(MEGA_CORP_PUBKEY, CHARLIE_PUBKEY) { LOC.Commands.Termination() }
            command(CHARLIE_PUBKEY) {Cash.Commands.Move()}
            timestamp(Instant.now())
            this.`fails with`("the LOC properties do not remain the same");
        }
    }

}
