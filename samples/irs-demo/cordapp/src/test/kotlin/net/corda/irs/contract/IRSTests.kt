package net.corda.irs.contract

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.generateKeyPair
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.seconds
import net.corda.finance.DOLLARS
import net.corda.finance.EUR
import net.corda.finance.contracts.AccrualAdjustment
import net.corda.finance.contracts.BusinessCalendar
import net.corda.finance.contracts.DateRollConvention
import net.corda.finance.contracts.DayCountBasisDay
import net.corda.finance.contracts.DayCountBasisYear
import net.corda.finance.contracts.Expression
import net.corda.finance.contracts.Fix
import net.corda.finance.contracts.FixOf
import net.corda.finance.contracts.Frequency
import net.corda.finance.contracts.PaymentRule
import net.corda.finance.contracts.Tenor
import net.corda.node.services.api.IdentityServiceInternal
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.dsl.*
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import net.corda.testing.node.transaction
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals

private val TEST_TX_TIME = Instant.parse("2015-04-17T12:00:00.00Z")
private val DUMMY_PARTY = Party(CordaX500Name("Dummy", "Madrid", "ES"), generateKeyPair().public)
private val dummyNotary = TestIdentity(DUMMY_NOTARY_NAME, 20)
private val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
private val miniCorp = TestIdentity(CordaX500Name("MiniCorp", "London", "GB"))
private val ORACLE_PUBKEY = TestIdentity(CordaX500Name("Oracle", "London", "GB")).publicKey
private val DUMMY_NOTARY get() = dummyNotary.party
private val DUMMY_NOTARY_KEY get() = dummyNotary.keyPair
private val MEGA_CORP get() = megaCorp.party
private val MEGA_CORP_KEY get() = megaCorp.keyPair
private val MEGA_CORP_PUBKEY get() = megaCorp.publicKey
private val MINI_CORP get() = miniCorp.party
private val MINI_CORP_KEY get() = miniCorp.keyPair
fun createDummyIRS(irsSelect: Int): InterestRateSwap.State {
    return when (irsSelect) {
        1 -> {

            val fixedLeg = InterestRateSwap.FixedLeg(
                    fixedRatePayer = MEGA_CORP,
                    notional = 15900000.DOLLARS,
                    paymentFrequency = Frequency.SemiAnnual,
                    effectiveDate = LocalDate.of(2016, 3, 10),
                    effectiveDateAdjustment = null,
                    terminationDate = LocalDate.of(2026, 3, 10),
                    terminationDateAdjustment = null,
                    fixedRate = FixedRate(PercentageRatioUnit("1.677")),
                    dayCountBasisDay = DayCountBasisDay.D30,
                    dayCountBasisYear = DayCountBasisYear.Y360,
                    rollConvention = DateRollConvention.ModifiedFollowing,
                    dayInMonth = 10,
                    paymentRule = PaymentRule.InArrears,
                    paymentDelay = 3,
                    paymentCalendar = BusinessCalendar.getInstance("London", "NewYork"),
                    interestPeriodAdjustment = AccrualAdjustment.Adjusted
            )

            val floatingLeg = InterestRateSwap.FloatingLeg(
                    floatingRatePayer = MINI_CORP,
                    notional = 15900000.DOLLARS,
                    paymentFrequency = Frequency.Quarterly,
                    effectiveDate = LocalDate.of(2016, 3, 10),
                    effectiveDateAdjustment = null,
                    terminationDate = LocalDate.of(2026, 3, 10),
                    terminationDateAdjustment = null,
                    dayCountBasisDay = DayCountBasisDay.D30,
                    dayCountBasisYear = DayCountBasisYear.Y360,
                    rollConvention = DateRollConvention.ModifiedFollowing,
                    fixingRollConvention = DateRollConvention.ModifiedFollowing,
                    dayInMonth = 10,
                    resetDayInMonth = 10,
                    paymentRule = PaymentRule.InArrears,
                    paymentDelay = 3,
                    paymentCalendar = BusinessCalendar.getInstance("London", "NewYork"),
                    interestPeriodAdjustment = AccrualAdjustment.Adjusted,
                    fixingPeriodOffset = 2,
                    resetRule = PaymentRule.InAdvance,
                    fixingsPerPayment = Frequency.Quarterly,
                    fixingCalendar = BusinessCalendar.getInstance("London"),
                    index = "LIBOR",
                    indexSource = "TEL3750",
                    indexTenor = Tenor("3M")
            )

            val calculation = InterestRateSwap.Calculation(

                    // TODO: this seems to fail quite dramatically
                    //expression = "fixedLeg.notional * fixedLeg.fixedRate",

                    // TODO: How I want it to look
                    //expression = "( fixedLeg.notional * (fixedLeg.fixedRate)) - (floatingLeg.notional * (rateSchedule.get(context.getDate('currentDate'))))",

                    // How it's ended up looking, which I think is now broken but it's a WIP.
                    expression = Expression("( fixedLeg.notional.pennies * (fixedLeg.fixedRate.ratioUnit.value)) -" +
                            "(floatingLeg.notional.pennies * (calculation.fixingSchedule.get(context.getDate('currentDate')).rate.ratioUnit.value))"),

                    floatingLegPaymentSchedule = mutableMapOf(),
                    fixedLegPaymentSchedule = mutableMapOf()
            )

            val common = InterestRateSwap.Common(
                    baseCurrency = EUR,
                    eligibleCurrency = EUR,
                    eligibleCreditSupport = "Cash in an Eligible Currency",
                    independentAmounts = Amount(0, EUR),
                    threshold = Amount(0, EUR),
                    minimumTransferAmount = Amount(250000 * 100, EUR),
                    rounding = Amount(10000 * 100, EUR),
                    valuationDateDescription = "Every Local Business Day",
                    notificationTime = "2:00pm London",
                    resolutionTime = "2:00pm London time on the first LocalBusiness Day following the date on which the notice is given ",
                    interestRate = ReferenceRate("T3270", Tenor("6M"), "EONIA"),
                    addressForTransfers = "",
                    exposure = UnknownType(),
                    localBusinessDay = BusinessCalendar.getInstance("London"),
                    tradeID = "trade1",
                    hashLegalDocs = "put hash here",
                    dailyInterestAmount = Expression("(CashAmount * InterestRate ) / (fixedLeg.notional.currency.currencyCode.equals('GBP')) ? 365 : 360")
            )

            InterestRateSwap.State(fixedLeg = fixedLeg, floatingLeg = floatingLeg, calculation = calculation, common = common, oracle = DUMMY_PARTY)
        }
        2 -> {
            // 10y swap, we pay 1.3% fixed 30/360 semi, rec 3m usd libor act/360 Q on 25m notional (mod foll/adj on both sides)
            // I did a mock up start date 10/03/2015 – 10/03/2025 so you have 5 cashflows on float side that have been preset the rest are unknown

            val fixedLeg = InterestRateSwap.FixedLeg(
                    fixedRatePayer = MEGA_CORP,
                    notional = 25000000.DOLLARS,
                    paymentFrequency = Frequency.SemiAnnual,
                    effectiveDate = LocalDate.of(2015, 3, 10),
                    effectiveDateAdjustment = null,
                    terminationDate = LocalDate.of(2025, 3, 10),
                    terminationDateAdjustment = null,
                    fixedRate = FixedRate(PercentageRatioUnit("1.3")),
                    dayCountBasisDay = DayCountBasisDay.D30,
                    dayCountBasisYear = DayCountBasisYear.Y360,
                    rollConvention = DateRollConvention.ModifiedFollowing,
                    dayInMonth = 10,
                    paymentRule = PaymentRule.InArrears,
                    paymentDelay = 0,
                    paymentCalendar = BusinessCalendar.getInstance(),
                    interestPeriodAdjustment = AccrualAdjustment.Adjusted
            )

            val floatingLeg = InterestRateSwap.FloatingLeg(
                    floatingRatePayer = MINI_CORP,
                    notional = 25000000.DOLLARS,
                    paymentFrequency = Frequency.Quarterly,
                    effectiveDate = LocalDate.of(2015, 3, 10),
                    effectiveDateAdjustment = null,
                    terminationDate = LocalDate.of(2025, 3, 10),
                    terminationDateAdjustment = null,
                    dayCountBasisDay = DayCountBasisDay.DActual,
                    dayCountBasisYear = DayCountBasisYear.Y360,
                    rollConvention = DateRollConvention.ModifiedFollowing,
                    fixingRollConvention = DateRollConvention.ModifiedFollowing,
                    dayInMonth = 10,
                    resetDayInMonth = 10,
                    paymentRule = PaymentRule.InArrears,
                    paymentDelay = 0,
                    paymentCalendar = BusinessCalendar.getInstance(),
                    interestPeriodAdjustment = AccrualAdjustment.Adjusted,
                    fixingPeriodOffset = 2,
                    resetRule = PaymentRule.InAdvance,
                    fixingsPerPayment = Frequency.Quarterly,
                    fixingCalendar = BusinessCalendar.getInstance(),
                    index = "USD LIBOR",
                    indexSource = "TEL3750",
                    indexTenor = Tenor("3M")
            )

            val calculation = InterestRateSwap.Calculation(

                    // TODO: this seems to fail quite dramatically
                    //expression = "fixedLeg.notional * fixedLeg.fixedRate",

                    // TODO: How I want it to look
                    //expression = "( fixedLeg.notional * (fixedLeg.fixedRate)) - (floatingLeg.notional * (rateSchedule.get(context.getDate('currentDate'))))",

                    // How it's ended up looking, which I think is now broken but it's a WIP.
                    expression = Expression("( fixedLeg.notional.pennies * (fixedLeg.fixedRate.ratioUnit.value)) -" +
                            "(floatingLeg.notional.pennies * (calculation.fixingSchedule.get(context.getDate('currentDate')).rate.ratioUnit.value))"),

                    floatingLegPaymentSchedule = mutableMapOf(),
                    fixedLegPaymentSchedule = mutableMapOf()
            )

            val common = InterestRateSwap.Common(
                    baseCurrency = EUR,
                    eligibleCurrency = EUR,
                    eligibleCreditSupport = "Cash in an Eligible Currency",
                    independentAmounts = Amount(0, EUR),
                    threshold = Amount(0, EUR),
                    minimumTransferAmount = Amount(250000 * 100, EUR),
                    rounding = Amount(10000 * 100, EUR),
                    valuationDateDescription = "Every Local Business Day",
                    notificationTime = "2:00pm London",
                    resolutionTime = "2:00pm London time on the first LocalBusiness Day following the date on which the notice is given ",
                    interestRate = ReferenceRate("T3270", Tenor("6M"), "EONIA"),
                    addressForTransfers = "",
                    exposure = UnknownType(),
                    localBusinessDay = BusinessCalendar.getInstance("London"),
                    tradeID = "trade2",
                    hashLegalDocs = "put hash here",
                    dailyInterestAmount = Expression("(CashAmount * InterestRate ) / (fixedLeg.notional.currency.currencyCode.equals('GBP')) ? 365 : 360")
            )

            return InterestRateSwap.State(fixedLeg = fixedLeg, floatingLeg = floatingLeg, calculation = calculation, common = common, oracle = DUMMY_PARTY)

        }
        else -> TODO("IRS number $irsSelect not defined")
    }
}

class IRSTests {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()
    private val megaCorpServices = MockServices(listOf("net.corda.irs.contract"), MEGA_CORP.name, rigorousMock(), MEGA_CORP_KEY)
    private val miniCorpServices = MockServices(listOf("net.corda.irs.contract"), MINI_CORP.name, rigorousMock(), MINI_CORP_KEY)
    private val notaryServices = MockServices(listOf("net.corda.irs.contract"), DUMMY_NOTARY.name, rigorousMock(), DUMMY_NOTARY_KEY)
    private val ledgerServices
        get() = MockServices(emptyList(), MEGA_CORP.name, rigorousMock<IdentityServiceInternal>().also {
            doReturn(MEGA_CORP).whenever(it).partyFromKey(MEGA_CORP_PUBKEY)
            doReturn(null).whenever(it).partyFromKey(ORACLE_PUBKEY)
        })

    @Test
    fun ok() {
        trade().verifies()
    }

    @Test
    fun `ok with groups`() {
        tradegroups().verifies()
    }

    /**
     * Generate an IRS txn - we'll need it for a few things.
     */
    private fun generateIRSTxn(irsSelect: Int): SignedTransaction {
        val dummyIRS = createDummyIRS(irsSelect)
        return run {
            val gtx = InterestRateSwap().generateAgreement(
                    fixedLeg = dummyIRS.fixedLeg,
                    floatingLeg = dummyIRS.floatingLeg,
                    calculation = dummyIRS.calculation,
                    common = dummyIRS.common,
                    oracle = DUMMY_PARTY,
                    notary = DUMMY_NOTARY).apply {
                setTimeWindow(TEST_TX_TIME, 30.seconds)
            }
            val ptx1 = megaCorpServices.signInitialTransaction(gtx)
            val ptx2 = miniCorpServices.addSignature(ptx1)
            notaryServices.addSignature(ptx2)
        }
    }

    /**
     * Just make sure it's sane.
     */
    @Test
    fun pprintIRS() {
        val irs = singleIRS()
        println(irs.prettyPrint())
    }

    /**
     * Utility so I don't have to keep typing this.
     */
    fun singleIRS(irsSelector: Int = 1): InterestRateSwap.State {
        return generateIRSTxn(irsSelector).tx.outputsOfType<InterestRateSwap.State>().single()
    }

    /**
     * Test the generate. No explicit exception as if something goes wrong, we'll find out anyway.
     */
    @Test
    fun generateIRS() {
        // Tests aren't allowed to return things
        generateIRSTxn(1)
    }

    /**
     * Testing a simple IRS, add a few fixings and then display as CSV.
     */
    @Test
    fun `IRS Export test`() {
        // No transactions etc required - we're just checking simple maths and export functionallity
        val irs = singleIRS(2)

        var newCalculation = irs.calculation

        val fixings = mapOf(LocalDate.of(2015, 3, 6) to "0.6",
                LocalDate.of(2015, 6, 8) to "0.75",
                LocalDate.of(2015, 9, 8) to "0.8",
                LocalDate.of(2015, 12, 8) to "0.55",
                LocalDate.of(2016, 3, 8) to "0.644")

        for ((key, value) in fixings) {
            newCalculation = newCalculation.applyFixing(key, FixedRate(PercentageRatioUnit(value)))
        }

        val newIRS = InterestRateSwap.State(irs.fixedLeg, irs.floatingLeg, newCalculation, irs.common, DUMMY_PARTY)
        println(newIRS.exportIRSToCSV())
    }

    /**
     * Make sure it has a schedule and the schedule has some unfixed rates.
     */
    @Test
    fun `next fixing date`() {
        val irs = singleIRS(1)
        println(irs.calculation.nextFixingDate())
    }

    /**
     * Iterate through all the fix dates and add something.
     */
    @Test
    fun generateIRSandFixSome() {
        val services = MockServices(listOf("net.corda.irs.contract"), MEGA_CORP.name,
                rigorousMock<IdentityServiceInternal>().also {
                    listOf(MEGA_CORP, MINI_CORP).forEach { party ->
                        doReturn(party).whenever(it).partyFromKey(party.owningKey)
                    }
                })
        var previousTXN = generateIRSTxn(1)
        previousTXN.toLedgerTransaction(services).verify()
        services.recordTransactions(previousTXN)
        fun currentIRS() = previousTXN.tx.outputsOfType<InterestRateSwap.State>().single()

        while (true) {
            val nextFix: FixOf = currentIRS().nextFixingOf() ?: break
            val fixTX: SignedTransaction = run {
                val tx = TransactionBuilder(DUMMY_NOTARY)
                val fixing = Fix(nextFix, "0.052".percent.value)
                InterestRateSwap().generateFix(tx, previousTXN.tx.outRef(0), fixing)
                tx.setTimeWindow(TEST_TX_TIME, 30.seconds)
                val ptx1 = megaCorpServices.signInitialTransaction(tx)
                val ptx2 = miniCorpServices.addSignature(ptx1)
                notaryServices.addSignature(ptx2)
            }
            fixTX.toLedgerTransaction(services).verify()
            services.recordTransactions(fixTX)
            previousTXN = fixTX
        }
    }

    // Move these later as they aren't IRS specific.
    @Test
    fun `test some rate objects 100 * FixedRate(5%)`() {
        val r1 = FixedRate(PercentageRatioUnit("5"))
        assertEquals(5, 100 * r1)
    }

    @Test
    fun `expression calculation testing`() {
        val dummyIRS = singleIRS()
        val stuffToPrint: ArrayList<String> = arrayListOf(
                "fixedLeg.notional.quantity",
                "fixedLeg.fixedRate.ratioUnit",
                "fixedLeg.fixedRate.ratioUnit.value",
                "floatingLeg.notional.quantity",
                "fixedLeg.fixedRate",
                "currentBusinessDate",
                "calculation.floatingLegPaymentSchedule.get(currentBusinessDate)",
                "fixedLeg.notional.token.currencyCode",
                "fixedLeg.notional.quantity * 10",
                "fixedLeg.notional.quantity * fixedLeg.fixedRate.ratioUnit.value",
                "(fixedLeg.notional.token.currencyCode.equals('GBP')) ? 365 : 360 ",
                "(fixedLeg.notional.quantity * (fixedLeg.fixedRate.ratioUnit.value))"
                // "calculation.floatingLegPaymentSchedule.get(context.getDate('currentDate')).rate"
                // "calculation.floatingLegPaymentSchedule.get(context.getDate('currentDate')).rate.ratioUnit.value",
                //"( fixedLeg.notional.pennies * (fixedLeg.fixedRate.ratioUnit.value)) - (floatingLeg.notional.pennies * (calculation.fixingSchedule.get(context.getDate('currentDate')).rate.ratioUnit.value))",
                // "( fixedLeg.notional * fixedLeg.fixedRate )"
        )

        for (i in stuffToPrint) {
            println(i)
            val z = dummyIRS.evaluateCalculation(LocalDate.of(2016, 9, 15), Expression(i))
            println(z.javaClass)
            println(z)
            println("-----------")
        }
        // This does not throw an exception in the test itself; it evaluates the above and they will throw if they do not pass.
    }


    /**
     * Generates a typical transactional history for an IRS.
     */
    fun trade(): LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter> {

        val ld = LocalDate.of(2016, 3, 8)
        val bd = BigDecimal("0.0063518")
        return ledgerServices.ledger(DUMMY_NOTARY) {
            transaction("Agreement") {
                attachments(IRS_PROGRAM_ID)
                output(IRS_PROGRAM_ID, "irs post agreement", singleIRS())
                command(MEGA_CORP_PUBKEY, InterestRateSwap.Commands.Agree())
                timeWindow(TEST_TX_TIME)
                this.verifies()
            }

            transaction("Fix") {
                attachments(IRS_PROGRAM_ID)
                input("irs post agreement")
                val postAgreement = "irs post agreement".output<InterestRateSwap.State>()
                output(IRS_PROGRAM_ID, "irs post first fixing",
                        postAgreement.copy(
                                postAgreement.fixedLeg,
                                postAgreement.floatingLeg,
                                postAgreement.calculation.applyFixing(ld, FixedRate(RatioUnit(bd))),
                                postAgreement.common))
                command(ORACLE_PUBKEY,
                        InterestRateSwap.Commands.Refix(Fix(FixOf("ICE LIBOR", ld, Tenor("3M")), bd)))
                timeWindow(TEST_TX_TIME)
                this.verifies()
            }
        }
    }

    private fun transaction(script: TransactionDSL<TransactionDSLInterpreter>.() -> EnforceVerifyOrFail) = run {
        ledgerServices.transaction(DUMMY_NOTARY, script)
    }

    @Test
    fun `ensure failure occurs when there are inbound states for an agreement command`() {
        val irs = singleIRS()
        transaction {
            attachments(IRS_PROGRAM_ID)
            input(IRS_PROGRAM_ID, irs)
            output(IRS_PROGRAM_ID, "irs post agreement", irs)
            command(MEGA_CORP_PUBKEY, InterestRateSwap.Commands.Agree())
            timeWindow(TEST_TX_TIME)
            this `fails with` "There are no in states for an agreement"
        }
    }

    @Test
    fun `ensure failure occurs when no events in fix schedule`() {
        val irs = singleIRS()
        val emptySchedule = mutableMapOf<LocalDate, FixedRatePaymentEvent>()
        transaction {
            attachments(IRS_PROGRAM_ID)
            output(IRS_PROGRAM_ID, irs.copy(calculation = irs.calculation.copy(fixedLegPaymentSchedule = emptySchedule)))
            command(MEGA_CORP_PUBKEY, InterestRateSwap.Commands.Agree())
            timeWindow(TEST_TX_TIME)
            this `fails with` "There are events in the fix schedule"
        }
    }

    @Test
    fun `ensure failure occurs when no events in floating schedule`() {
        val irs = singleIRS()
        val emptySchedule = mutableMapOf<LocalDate, FloatingRatePaymentEvent>()
        transaction {
            attachments(IRS_PROGRAM_ID)
            output(IRS_PROGRAM_ID, irs.copy(calculation = irs.calculation.copy(floatingLegPaymentSchedule = emptySchedule)))
            command(MEGA_CORP_PUBKEY, InterestRateSwap.Commands.Agree())
            timeWindow(TEST_TX_TIME)
            this `fails with` "There are events in the float schedule"
        }
    }

    @Test
    fun `ensure notionals are non zero`() {
        val irs = singleIRS()
        transaction {
            attachments(IRS_PROGRAM_ID)
            output(IRS_PROGRAM_ID, irs.copy(irs.fixedLeg.copy(notional = irs.fixedLeg.notional.copy(quantity = 0))))
            command(MEGA_CORP_PUBKEY, InterestRateSwap.Commands.Agree())
            timeWindow(TEST_TX_TIME)
            this `fails with` "All notionals must be non zero"
        }

        transaction {
            attachments(IRS_PROGRAM_ID)
            output(IRS_PROGRAM_ID, irs.copy(irs.fixedLeg.copy(notional = irs.floatingLeg.notional.copy(quantity = 0))))
            command(MEGA_CORP_PUBKEY, InterestRateSwap.Commands.Agree())
            timeWindow(TEST_TX_TIME)
            this `fails with` "All notionals must be non zero"
        }
    }

    @Test
    fun `ensure positive rate on fixed leg`() {
        val irs = singleIRS()
        val modifiedIRS = irs.copy(fixedLeg = irs.fixedLeg.copy(fixedRate = FixedRate(PercentageRatioUnit("-0.1"))))
        transaction {
            attachments(IRS_PROGRAM_ID)
            output(IRS_PROGRAM_ID, modifiedIRS)
            command(MEGA_CORP_PUBKEY, InterestRateSwap.Commands.Agree())
            timeWindow(TEST_TX_TIME)
            this `fails with` "The fixed leg rate must be positive"
        }
    }

    /**
     * This will be modified once we adapt the IRS to be cross currency.
     */
    @Test
    fun `ensure same currency notionals`() {
        val irs = singleIRS()
        val modifiedIRS = irs.copy(fixedLeg = irs.fixedLeg.copy(notional = Amount(irs.fixedLeg.notional.quantity, Currency.getInstance("JPY"))))
        transaction {
            attachments(IRS_PROGRAM_ID)
            output(IRS_PROGRAM_ID, modifiedIRS)
            command(MEGA_CORP_PUBKEY, InterestRateSwap.Commands.Agree())
            timeWindow(TEST_TX_TIME)
            this `fails with` "The currency of the notionals must be the same"
        }
    }

    @Test
    fun `ensure notional amounts are equal`() {
        val irs = singleIRS()
        val modifiedIRS = irs.copy(fixedLeg = irs.fixedLeg.copy(notional = Amount(irs.floatingLeg.notional.quantity + 1, irs.floatingLeg.notional.token)))
        transaction {
            attachments(IRS_PROGRAM_ID)
            output(IRS_PROGRAM_ID, modifiedIRS)
            command(MEGA_CORP_PUBKEY, InterestRateSwap.Commands.Agree())
            timeWindow(TEST_TX_TIME)
            this `fails with` "All leg notionals must be the same"
        }
    }

    @Test
    fun `ensure trade date and termination date checks are done pt1`() {
        val irs = singleIRS()
        val modifiedIRS1 = irs.copy(fixedLeg = irs.fixedLeg.copy(terminationDate = irs.fixedLeg.effectiveDate.minusDays(1)))
        transaction {
            attachments(IRS_PROGRAM_ID)
            output(IRS_PROGRAM_ID, modifiedIRS1)
            command(MEGA_CORP_PUBKEY, InterestRateSwap.Commands.Agree())
            timeWindow(TEST_TX_TIME)
            this `fails with` "The effective date is before the termination date for the fixed leg"
        }

        val modifiedIRS2 = irs.copy(floatingLeg = irs.floatingLeg.copy(terminationDate = irs.floatingLeg.effectiveDate.minusDays(1)))
        transaction {
            attachments(IRS_PROGRAM_ID)
            output(IRS_PROGRAM_ID, modifiedIRS2)
            command(MEGA_CORP_PUBKEY, InterestRateSwap.Commands.Agree())
            timeWindow(TEST_TX_TIME)
            this `fails with` "The effective date is before the termination date for the floating leg"
        }
    }

    @Test
    fun `ensure trade date and termination date checks are done pt2`() {
        val irs = singleIRS()

        val modifiedIRS3 = irs.copy(floatingLeg = irs.floatingLeg.copy(terminationDate = irs.fixedLeg.terminationDate.minusDays(1)))
        transaction {
            attachments(IRS_PROGRAM_ID)
            output(IRS_PROGRAM_ID, modifiedIRS3)
            command(MEGA_CORP_PUBKEY, InterestRateSwap.Commands.Agree())
            timeWindow(TEST_TX_TIME)
            this `fails with` "The termination dates are aligned"
        }


        val modifiedIRS4 = irs.copy(floatingLeg = irs.floatingLeg.copy(effectiveDate = irs.fixedLeg.effectiveDate.minusDays(1)))
        transaction {
            attachments(IRS_PROGRAM_ID)
            output(IRS_PROGRAM_ID, modifiedIRS4)
            command(MEGA_CORP_PUBKEY, InterestRateSwap.Commands.Agree())
            timeWindow(TEST_TX_TIME)
            this `fails with` "The effective dates are aligned"
        }
    }


    @Test
    fun `various fixing tests`() {
        val ld = LocalDate.of(2016, 3, 8)
        val bd = BigDecimal("0.0063518")

        transaction {
            attachments(IRS_PROGRAM_ID)
            output(IRS_PROGRAM_ID, "irs post agreement", singleIRS())
            command(MEGA_CORP_PUBKEY, InterestRateSwap.Commands.Agree())
            timeWindow(TEST_TX_TIME)
            this.verifies()
        }

        val oldIRS = singleIRS(1)
        val newIRS = oldIRS.copy(oldIRS.fixedLeg,
                oldIRS.floatingLeg,
                oldIRS.calculation.applyFixing(ld, FixedRate(RatioUnit(bd))),
                oldIRS.common)

        transaction {
            attachments(IRS_PROGRAM_ID)
            input(IRS_PROGRAM_ID, oldIRS)

            // Templated tweak for reference. A corrent fixing applied should be ok
            tweak {
                command(ORACLE_PUBKEY,
                        InterestRateSwap.Commands.Refix(Fix(FixOf("ICE LIBOR", ld, Tenor("3M")), bd)))
                timeWindow(TEST_TX_TIME)
                output(IRS_PROGRAM_ID, newIRS)
                this.verifies()
            }

            // This test makes sure that verify confirms the fixing was applied and there is a difference in the old and new
            tweak {
                command(ORACLE_PUBKEY, InterestRateSwap.Commands.Refix(Fix(FixOf("ICE LIBOR", ld, Tenor("3M")), bd)))
                timeWindow(TEST_TX_TIME)
                output(IRS_PROGRAM_ID, oldIRS)
                this `fails with` "There is at least one difference in the IRS floating leg payment schedules"
            }

            // This tests tries to sneak in a change to another fixing (which may or may not be the latest one)
            tweak {
                command(ORACLE_PUBKEY, InterestRateSwap.Commands.Refix(Fix(FixOf("ICE LIBOR", ld, Tenor("3M")), bd)))
                timeWindow(TEST_TX_TIME)

                val firstResetKey = newIRS.calculation.floatingLegPaymentSchedule.keys.toList()[1]
                val firstResetValue = newIRS.calculation.floatingLegPaymentSchedule[firstResetKey]
                val modifiedFirstResetValue = firstResetValue!!.copy(notional = Amount(firstResetValue.notional.quantity, Currency.getInstance("JPY")))
                output(IRS_PROGRAM_ID,
                        newIRS.copy(
                                newIRS.fixedLeg,
                                newIRS.floatingLeg,
                                newIRS.calculation.copy(floatingLegPaymentSchedule = newIRS.calculation.floatingLegPaymentSchedule.plus(
                                        Pair(firstResetKey, modifiedFirstResetValue))),
                                newIRS.common))
                this `fails with` "There is only one change in the IRS floating leg payment schedule"
            }

            // This tests modifies the payment currency for the fixing
            tweak {
                command(ORACLE_PUBKEY, InterestRateSwap.Commands.Refix(Fix(FixOf("ICE LIBOR", ld, Tenor("3M")), bd)))
                timeWindow(TEST_TX_TIME)

                val latestReset = newIRS.calculation.floatingLegPaymentSchedule.filter { it.value.rate is FixedRate }.maxBy { it.key }
                val modifiedLatestResetValue = latestReset!!.value.copy(notional = Amount(latestReset.value.notional.quantity, Currency.getInstance("JPY")))
                output(IRS_PROGRAM_ID,
                        newIRS.copy(
                                newIRS.fixedLeg,
                                newIRS.floatingLeg,
                                newIRS.calculation.copy(floatingLegPaymentSchedule = newIRS.calculation.floatingLegPaymentSchedule.plus(
                                        Pair(latestReset.key, modifiedLatestResetValue))),
                                newIRS.common))
                this `fails with` "The fix payment has the same currency as the notional"
            }
        }
    }


    /**
     * This returns an example of transactions that are grouped by TradeId and then a fixing applied.
     * It's important to make the tradeID different for two reasons, the hashes will be the same and all sorts of confusion will
     * result and the grouping won't work either.
     * In reality, the only fields that should be in common will be the next fixing date and the reference rate.
     */
    fun tradegroups(): LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter> {
        val ld1 = LocalDate.of(2016, 3, 8)
        val bd1 = BigDecimal("0.0063518")

        val irs = singleIRS()
        return ledgerServices.ledger(DUMMY_NOTARY) {
            transaction("Agreement") {
                attachments(IRS_PROGRAM_ID)
                output(IRS_PROGRAM_ID, "irs post agreement1",
                        irs.copy(
                                irs.fixedLeg,
                                irs.floatingLeg,
                                irs.calculation,
                                irs.common.copy(tradeID = "t1")))
                command(MEGA_CORP_PUBKEY, InterestRateSwap.Commands.Agree())
                timeWindow(TEST_TX_TIME)
                this.verifies()
            }

            transaction("Agreement") {
                attachments(IRS_PROGRAM_ID)
                output(IRS_PROGRAM_ID, "irs post agreement2",
                        irs.copy(
                                linearId = UniqueIdentifier("t2"),
                                fixedLeg = irs.fixedLeg,
                                floatingLeg = irs.floatingLeg,
                                calculation = irs.calculation,
                                common = irs.common.copy(tradeID = "t2")))
                command(MEGA_CORP_PUBKEY, InterestRateSwap.Commands.Agree())
                timeWindow(TEST_TX_TIME)
                this.verifies()
            }

            transaction("Fix") {
                attachments(IRS_PROGRAM_ID)
                input("irs post agreement1")
                input("irs post agreement2")
                val postAgreement1 = "irs post agreement1".output<InterestRateSwap.State>()
                output(IRS_PROGRAM_ID, "irs post first fixing1",
                        postAgreement1.copy(
                                postAgreement1.fixedLeg,
                                postAgreement1.floatingLeg,
                                postAgreement1.calculation.applyFixing(ld1, FixedRate(RatioUnit(bd1))),
                                postAgreement1.common.copy(tradeID = "t1")))
                val postAgreement2 = "irs post agreement2".output<InterestRateSwap.State>()
                output(IRS_PROGRAM_ID, "irs post first fixing2",
                        postAgreement2.copy(
                                postAgreement2.fixedLeg,
                                postAgreement2.floatingLeg,
                                postAgreement2.calculation.applyFixing(ld1, FixedRate(RatioUnit(bd1))),
                                postAgreement2.common.copy(tradeID = "t2")))
                command(ORACLE_PUBKEY,
                        InterestRateSwap.Commands.Refix(Fix(FixOf("ICE LIBOR", ld1, Tenor("3M")), bd1)))
                timeWindow(TEST_TX_TIME)
                this.verifies()
            }
        }
    }
}