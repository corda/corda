package com.r3corda.contracts

import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.node.services.testing.MockServices
import com.r3corda.core.testing.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.*


class BillOfLadingAgreementTests {
    val pros = BillOfLadingAgreement.BillOfLadingProperties(
            billOfLadingID = "billOfLadingID",
            issueDate = LocalDate.now(),
            carrierOwner = ALICE,
            nameOfVessel = "Karaboudjan",
            descriptionOfGoods = listOf(LocDataStructures.Good(description="Crab meet cans",quantity = 10000,grossWeight = null)),
            dateOfShipment = LocalDate.now(),
            portOfLoading = LocDataStructures.Port(country = "Morokko",city = "Larache",address = null,state = null,name=null),
            portOfDischarge = LocDataStructures.Port(country = "Belgium",city = "Antwerpen",address = null,state = null,name=null),
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
    val Bill = BillOfLadingAgreement.State(
            owner = MEGA_CORP_PUBKEY,
            beneficiary = BOB,
            props =pros
    )

    lateinit var services: MockServices

    @Before
    fun setup() {
        services = MockServices()
    }

    //Generation method tests

    @Test
    fun issueGenerationMethod() {
        val ptx = BillOfLadingAgreement().generateIssue(Bill.owner, Bill.beneficiary, Bill.props, DUMMY_NOTARY).apply {
            signWith(ALICE_KEY)
            signWith(DUMMY_NOTARY_KEY)
        }
        ptx.toSignedTransaction().toLedgerTransaction(services).verify()
    }

    @Test(expected = IllegalStateException::class)
    fun issueGenerationMethod_Unsigned() {
        val ptx = BillOfLadingAgreement().generateIssue(Bill.owner, Bill.beneficiary, Bill.props, DUMMY_NOTARY)
        val stx = ptx.toSignedTransaction()
        ptx.toSignedTransaction().toLedgerTransaction(services).verify()
    }

    @Test(expected = IllegalStateException::class)
    fun issueGenerationMethod_KeyMismatch() {
        val ptx = BillOfLadingAgreement().generateIssue(Bill.owner, Bill.beneficiary, Bill.props, DUMMY_NOTARY).apply {
            signWith(BOB_KEY)
        }
        ptx.toSignedTransaction().toLedgerTransaction(services).verify()
    }

     // @Test // TODO: Fix Test
    fun transferAndEndorseGenerationMethod() {

        val ptx:TransactionBuilder = TransactionType.General.Builder(notary = DUMMY_NOTARY)
        val sr = StateAndRef(
                TransactionState(Bill, DUMMY_NOTARY),
                StateRef(SecureHash.randomSHA256(), Random().nextInt(32))
        )
        BillOfLadingAgreement().generateTransferAndEndorse(ptx,sr,CHARLIE_PUBKEY, CHARLIE)
        ptx.signWith(MEGA_CORP_KEY) //Signed by owner
        ptx.signWith(BOB_KEY)       //and beneficiary
       // ptx.signWith(CHARLIE_KEY) // ??????
        ptx.toSignedTransaction().toLedgerTransaction(services).verify()
    }

    @Test(expected = IllegalStateException::class)
    fun transferAndEndorseGenerationMethod_MissingBeneficiarySignature() {
        val ptx:TransactionBuilder = TransactionType.General.Builder()
        val sr = StateAndRef(
                TransactionState(Bill, DUMMY_NOTARY),
                StateRef(SecureHash.randomSHA256(), Random().nextInt(32))
        )
        BillOfLadingAgreement().generateTransferAndEndorse(ptx,sr,CHARLIE_PUBKEY, CHARLIE)
        ptx.signWith(MEGA_CORP_KEY) //Signed by owner
        ptx.toSignedTransaction().toLedgerTransaction(services).verify()
    }

    @Test(expected = IllegalStateException::class)
    fun transferAndEndorseGenerationMethod_MissingOwnerSignature() {
        val ptx:TransactionBuilder = TransactionType.General.Builder()
        val sr = StateAndRef(
                TransactionState(Bill, DUMMY_NOTARY),
                StateRef(SecureHash.randomSHA256(), Random().nextInt(32))
        )
        BillOfLadingAgreement().generateTransferAndEndorse(ptx,sr,CHARLIE_PUBKEY, CHARLIE)
        ptx.signWith(BOB_KEY) //Signed by beneficiary
        ptx.toSignedTransaction().toLedgerTransaction(services).verify()
    }

   // @Test // TODO Fix Test
    fun transferPossessionGenerationMethod() {
        val ptx:TransactionBuilder = TransactionType.General.Builder(notary = DUMMY_NOTARY)
        val sr = StateAndRef(
                TransactionState(Bill, DUMMY_NOTARY),
                StateRef(SecureHash.randomSHA256(), Random().nextInt(32))
        )
        BillOfLadingAgreement().generateTransferPossession(ptx,sr,CHARLIE_PUBKEY)
        ptx.signWith(MEGA_CORP_KEY) //Signed by owner
        ptx.toSignedTransaction().toLedgerTransaction(services).verify()
    }

    @Test(expected = IllegalStateException::class)
    fun transferPossessionGenerationMethod_Unsigned() {
        val ptx:TransactionBuilder = TransactionType.General.Builder()
        val sr = StateAndRef(
                TransactionState(Bill, DUMMY_NOTARY),
                StateRef(SecureHash.randomSHA256(), Random().nextInt(32))
        )
        BillOfLadingAgreement().generateTransferPossession(ptx,sr,CHARLIE_PUBKEY)
        ptx.toSignedTransaction().toLedgerTransaction(services).verify()
    }

    //Custom transaction tests

    @Test
    fun generalConsistencyTests() {
        transaction {
            input { Bill }
            output { Bill.copy(owner = CHARLIE_PUBKEY, beneficiary = CHARLIE) }
            command(MEGA_CORP_PUBKEY, BOB_PUBKEY) { BillOfLadingAgreement.Commands.TransferAndEndorseBL() }
            command(MEGA_CORP_PUBKEY) { BillOfLadingAgreement.Commands.TransferPossession() }
            timestamp(Instant.now())
            //There are multiple commands
            this `fails with` "List has more than one element."
        }
        transaction {
            input { Bill }
            output { Bill.copy(owner = CHARLIE_PUBKEY, beneficiary = CHARLIE) }
            timestamp(Instant.now())
            //There are no commands
            this `fails with` "Required ${BillOfLadingAgreement.Commands::class.qualifiedName} command"
        }

    }

    @Test
    fun issueTests() {
        transaction {
            output { Bill }
            command(ALICE_PUBKEY) { BillOfLadingAgreement.Commands.IssueBL() }
            timestamp(Instant.now())
            this.verifies()
        }

        transaction {
            input { Bill }
            output { Bill.copy(owner = CHARLIE_PUBKEY, beneficiary = CHARLIE) }
            command(MEGA_CORP_PUBKEY, BOB_PUBKEY) { BillOfLadingAgreement.Commands.IssueBL() }
            timestamp(Instant.now())
            this `fails with` "there is no input state"
        }

        transaction {
            output { Bill }
            command(BOB_PUBKEY) { BillOfLadingAgreement.Commands.IssueBL() }
            timestamp(Instant.now())
            this `fails with` "the transaction is signed by the carrier"
        }

    }

    @Test
    fun transferAndEndorseTests() {
        transaction {
            input { Bill }
            output { Bill.copy(owner = CHARLIE_PUBKEY, beneficiary = CHARLIE) }
            command(MEGA_CORP_PUBKEY, BOB_PUBKEY) { BillOfLadingAgreement.Commands.TransferAndEndorseBL() }
            timestamp(Instant.now())
            this.verifies()
        }

        transaction {
            input { Bill }
            output { Bill.copy(owner = CHARLIE_PUBKEY, beneficiary = CHARLIE) }
            command(MEGA_CORP_PUBKEY, BOB_PUBKEY) { BillOfLadingAgreement.Commands.TransferAndEndorseBL() }
            //There is no timestamp
            this `fails with` "must be timestamped"
        }

        transaction {
            input { Bill }
            input { Bill.copy(owner = CHARLIE_PUBKEY, beneficiary = CHARLIE) }
            output { Bill.copy(owner = CHARLIE_PUBKEY, beneficiary = CHARLIE) }
            command(MEGA_CORP_PUBKEY, BOB_PUBKEY) { BillOfLadingAgreement.Commands.TransferAndEndorseBL() }
            timestamp(Instant.now())
            //There are two inputs
            this `fails with` "List has more than one element."
        }

        transaction {
            output { Bill.copy(owner = CHARLIE_PUBKEY, beneficiary = CHARLIE) }
            command(MEGA_CORP_PUBKEY, BOB_PUBKEY) { BillOfLadingAgreement.Commands.TransferAndEndorseBL() }
            timestamp(Instant.now())
            //There are no inputs
            this `fails with` "List is empty."
        }

        transaction {
            input { Bill }
            output { Bill.copy(owner = CHARLIE_PUBKEY, beneficiary = CHARLIE) }
            output { Bill }
            command(MEGA_CORP_PUBKEY, BOB_PUBKEY) { BillOfLadingAgreement.Commands.TransferAndEndorseBL() }
            timestamp(Instant.now())
            //There are two outputs
            this `fails with` "List has more than one element."
        }

        transaction {
            input { Bill }
            command(MEGA_CORP_PUBKEY, BOB_PUBKEY) { BillOfLadingAgreement.Commands.TransferAndEndorseBL() }
            timestamp(Instant.now())
            //There are no outputs
            this `fails with` "List is empty."
        }

        transaction {
            input { Bill }
            output { Bill.copy(owner = CHARLIE_PUBKEY, beneficiary = CHARLIE) }
            command(MEGA_CORP_PUBKEY) { BillOfLadingAgreement.Commands.TransferAndEndorseBL() }
            timestamp(Instant.now())
            this `fails with` "the transaction is signed by the beneficiary"
        }

        transaction {
            input { Bill }
            output { Bill.copy(owner = CHARLIE_PUBKEY, beneficiary = CHARLIE) }
            command(BOB_PUBKEY) { BillOfLadingAgreement.Commands.TransferAndEndorseBL() }
            timestamp(Instant.now())
            this `fails with` "the transaction is signed by the state object owner"
        }

        transaction {
            input { Bill }
            output { Bill.copy(owner = CHARLIE_PUBKEY, beneficiary = CHARLIE, props = pros.copy(nameOfVessel = "Svet")) }
            command(MEGA_CORP_PUBKEY, BOB_PUBKEY) { BillOfLadingAgreement.Commands.TransferAndEndorseBL() }
            timestamp(Instant.now())
            this `fails with` "the bill of lading agreement properties are unchanged"
        }

    }

    @Test
    fun transferPossessionTests() {
        transaction {
            input { Bill }
            output { Bill.copy(owner = CHARLIE_PUBKEY) }
            command(MEGA_CORP_PUBKEY) { BillOfLadingAgreement.Commands.TransferPossession() }
            timestamp(Instant.now())
            this.verifies()
        }

        transaction {
            input { Bill }
            output { Bill.copy(owner = CHARLIE_PUBKEY) }
            command(MEGA_CORP_PUBKEY) { BillOfLadingAgreement.Commands.TransferPossession() }
            //There is no timestamp
            this `fails with` "must be timestamped"
        }

        transaction {
            input { Bill }
            input { Bill.copy(owner = BOB_PUBKEY) }
            output { Bill.copy(owner = CHARLIE_PUBKEY) }
            command(MEGA_CORP_PUBKEY) { BillOfLadingAgreement.Commands.TransferPossession() }
            timestamp(Instant.now())
            //There are two inputs
            this `fails with` "List has more than one element."
        }

        transaction {
            output { Bill.copy(owner = CHARLIE_PUBKEY) }
            command(MEGA_CORP_PUBKEY) { BillOfLadingAgreement.Commands.TransferPossession() }
            timestamp(Instant.now())
            //There are no inputs
            this `fails with` "List is empty."
        }

        transaction {
            input { Bill }
            output { Bill.copy(owner = CHARLIE_PUBKEY) }
            output { Bill.copy(owner = ALICE_PUBKEY) }
            command(MEGA_CORP_PUBKEY) { BillOfLadingAgreement.Commands.TransferPossession() }
            timestamp(Instant.now())
            //There are two outputs
            this `fails with` "List has more than one element."
        }

        transaction {
            input { Bill }
            command(MEGA_CORP_PUBKEY) { BillOfLadingAgreement.Commands.TransferPossession() }
            timestamp(Instant.now())
            //There are no outputs
            this `fails with` "List is empty."
        }

        transaction {
            input { Bill }
            output { Bill.copy(owner = CHARLIE_PUBKEY) }
            command(ALICE_PUBKEY) { BillOfLadingAgreement.Commands.TransferPossession() }
            timestamp(Instant.now())
            this `fails with` "the transaction is signed by the state object owner"
        }

        transaction {
            input { Bill }
            output { Bill.copy(owner = CHARLIE_PUBKEY,beneficiary = CHARLIE) }
            command(MEGA_CORP_PUBKEY) { BillOfLadingAgreement.Commands.TransferPossession() }
            timestamp(Instant.now())
            this `fails with` "the beneficiary is unchanged"
        }


        transaction {
            input { Bill }
            output { Bill.copy(owner = CHARLIE_PUBKEY, props = pros.copy(nameOfVessel = "Svet")) }
            command(MEGA_CORP_PUBKEY) { BillOfLadingAgreement.Commands.TransferPossession() }
            timestamp(Instant.now())
            this `fails with` "the bill of lading agreement properties are unchanged"
        }

    }

}
