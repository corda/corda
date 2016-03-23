/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package contracts

import core.*
import core.node.services.DummyTimestampingAuthority
import core.testutils.*
import org.junit.Test
import java.time.LocalDate
import java.util.*

fun createDummyIRS(irsSelect: Int): InterestRateSwap.State {
    return when(irsSelect) {
        1 -> {

            val fixedLeg = InterestRateSwap.FixedLeg(
                    fixedRatePayer = MEGA_CORP,
                    notional = 15900000.DOLLARS,
                    paymentFrequency = Frequency.SemiAnnual,
                    effectiveDate = LocalDate.of(2016, 3, 16),
                    effectiveDateAdjustment = null,
                    terminationDate = LocalDate.of(2026, 3, 16),
                    terminationDateAdjustment = null,
                    fixedRate = FixedRate(PercentageRatioUnit("1.677")),
                    dayCountBasisDay = DayCountBasisDay.D30,
                    dayCountBasisYear = DayCountBasisYear.Y360,
                    rollConvention = DateRollConvention.ModifiedFollowing,
                    dayInMonth = 10,
                    paymentRule = PaymentRule.InArrears,
                    paymentDelay = 0,
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
                    paymentDelay = 0,
                    paymentCalendar = BusinessCalendar.getInstance("London", "NewYork"),
                    interestPeriodAdjustment = AccrualAdjustment.Adjusted,
                    fixingPeriod = DateOffset.TWODAYS,
                    resetRule = PaymentRule.InAdvance,
                    fixingsPerPayment = Frequency.Quarterly,
                    fixingCalendar = BusinessCalendar.getInstance("London"),
                    index = "LIBOR",
                    indexSource = "TEL3750",
                    indexTenor = Tenor("3M")
            )

            val calculation = InterestRateSwap.Calculation (

                    // TODO: this seems to fail quite dramatically
                    //expression = "fixedLeg.notional * fixedLeg.fixedRate",

                    // TODO: How I want it to look
                    //expression = "( fixedLeg.notional * (fixedLeg.fixedRate)) - (floatingLeg.notional * (rateSchedule.get(context.getDate('currentDate'))))",

                    // How it's ended up looking, which I think is now broken but it's a WIP.
                    expression = Expression("( fixedLeg.notional.pennies * (fixedLeg.fixedRate.ratioUnit.value)) -" +
                            "(floatingLeg.notional.pennies * (calculation.fixingSchedule.get(context.getDate('currentDate')).rate.ratioUnit.value))"),

                    floatingLegPaymentSchedule = HashMap(),
                    fixedLegPaymentSchedule = HashMap()
            )

            val EUR = currency("EUR")

            val common = InterestRateSwap.Common(
                    baseCurrency = EUR,
                    eligibleCurrency = EUR,
                    eligibleCreditSupport = "Cash in an Eligible Currency",
                    independentAmounts = Amount(0, EUR),
                    threshold = Amount(0, EUR),
                    minimumTransferAmount = Amount(250000 * 100, EUR),
                    rounding = Amount(10000 * 100, EUR),
                    valuationDate = "Every Local Business Day",
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

            InterestRateSwap.State(fixedLeg = fixedLeg, floatingLeg = floatingLeg, calculation = calculation, common = common)
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
                    fixingPeriod = DateOffset.TWODAYS,
                    resetRule = PaymentRule.InAdvance,
                    fixingsPerPayment = Frequency.Quarterly,
                    fixingCalendar = BusinessCalendar.getInstance(),
                    index = "USD LIBOR",
                    indexSource = "TEL3750",
                    indexTenor = Tenor("3M")
            )

            val calculation = InterestRateSwap.Calculation (

                    // TODO: this seems to fail quite dramatically
                    //expression = "fixedLeg.notional * fixedLeg.fixedRate",

                    // TODO: How I want it to look
                    //expression = "( fixedLeg.notional * (fixedLeg.fixedRate)) - (floatingLeg.notional * (rateSchedule.get(context.getDate('currentDate'))))",

                    // How it's ended up looking, which I think is now broken but it's a WIP.
                    expression = Expression("( fixedLeg.notional.pennies * (fixedLeg.fixedRate.ratioUnit.value)) -" +
                            "(floatingLeg.notional.pennies * (calculation.fixingSchedule.get(context.getDate('currentDate')).rate.ratioUnit.value))"),

                    floatingLegPaymentSchedule = HashMap(),
                    fixedLegPaymentSchedule = HashMap()
            )

            val EUR = currency("EUR")

            val common = InterestRateSwap.Common(
                    baseCurrency = EUR,
                    eligibleCurrency = EUR,
                    eligibleCreditSupport = "Cash in an Eligible Currency",
                    independentAmounts = Amount(0, EUR),
                    threshold = Amount(0, EUR),
                    minimumTransferAmount = Amount(250000 * 100, EUR),
                    rounding = Amount(10000 * 100, EUR),
                    valuationDate = "Every Local Business Day",
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

            return InterestRateSwap.State(fixedLeg = fixedLeg, floatingLeg = floatingLeg, calculation = calculation, common = common)

        }
        else -> TODO("IRS number $irsSelect not defined")
    }
}

class IRSTests {

    val attachments = MockStorageService().attachments

    @Test
    fun ok() {
        val t = trade()
        t.verify()
    }

    /**
     * Generate an IRS txn - we'll need it for a few things.
     */
    fun generateIRSTxn(irsSelect: Int): LedgerTransaction {
        val dummyIRS = createDummyIRS(irsSelect)
        val genTX: LedgerTransaction = run {
            val gtx = InterestRateSwap().generateAgreement(
                    fixedLeg = dummyIRS.fixedLeg,
                    floatingLeg = dummyIRS.floatingLeg,
                    calculation = dummyIRS.calculation,
                    common = dummyIRS.common).apply {
                setTime(TEST_TX_TIME, DummyTimestampingAuthority.identity, 30.seconds)
                signWith(MEGA_CORP_KEY)
                signWith(MINI_CORP_KEY)
                timestamp(DUMMY_TIMESTAMPER)
            }

            val stx = gtx.toSignedTransaction()
            stx.verifyToLedgerTransaction(MockIdentityService, attachments)
        }
        return genTX
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
     * Utility so I don't have to keep typing this
     */
    fun singleIRS(irsSelector: Int = 1): InterestRateSwap.State {
        return generateIRSTxn(irsSelector).outputs.filterIsInstance<InterestRateSwap.State>().single()
    }

    /**
     * Test the generate
     */
    @Test
    fun generateIRS() {
        // Tests aren't allowed to return things
        generateIRSTxn(1)
    }

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

        for (it in fixings) {
            newCalculation = newCalculation.applyFixing(it.key, FixedRate(PercentageRatioUnit(it.value)))
        }

        val newIRS = InterestRateSwap.State(irs.fixedLeg, irs.floatingLeg, newCalculation, irs.common)
        println(newIRS.exportIRSToCSV())
    }

    /**
     * Make sure it has a schedule and the schedule has some unfixed rates
     */
    @Test
    fun `next fixing date`() {
        val irs = singleIRS(1)
        println(irs.calculation.nextFixingDate())
    }

    /**
     * Iterate through all the fix dates and add something
     */
    @Test
    fun generateIRSandFixSome() {
        var previousTXN = generateIRSTxn(1)
        var currentIRS = previousTXN.outputs.filterIsInstance<InterestRateSwap.State>().single()
        println(currentIRS.prettyPrint())
        while (true) {
            val nextFixingDate = currentIRS.calculation.nextFixingDate() ?: break
            println("\n\n\n ***** Applying a fixing to $nextFixingDate \n\n\n")
            var fixTX: LedgerTransaction = run {
                val tx = TransactionBuilder()
                val fixing = Pair(nextFixingDate, FixedRate("0.052".percent))
                InterestRateSwap().generateFix(tx, previousTXN.outRef(0), fixing)
                with(tx) {
                    setTime(TEST_TX_TIME, DummyTimestampingAuthority.identity, 30.seconds)
                    signWith(MEGA_CORP_KEY)
                    signWith(MINI_CORP_KEY)
                    timestamp(DUMMY_TIMESTAMPER)
                }
                val stx = tx.toSignedTransaction()
                stx.verifyToLedgerTransaction(MockIdentityService, attachments)
            }
            currentIRS = previousTXN.outputs.filterIsInstance<InterestRateSwap.State>().single()
            println(currentIRS.prettyPrint())
            previousTXN = fixTX
        }
    }

    // Move these later as they aren't IRS specific.
    @Test
    fun `test some rate objects 100 * FixedRate(5%)`() {
        val r1 = FixedRate(PercentageRatioUnit("5"))
        assert(100 * r1 == 5)
    }

    @Test
    fun `more rate tests`() {
        val r1 = FixedRate(PercentageRatioUnit("10"))
        val r2 = FixedRate(PercentageRatioUnit("10"))

        // TODO: r1+r2 ? Do we want to allow these.
        // TODO: r1*r2 ?
    }

    @Test
    fun `expression calculation testing`() {
        val dummyIRS = singleIRS()
        val v = FixedRate(PercentageRatioUnit("4.5"))
        val stuffToPrint: ArrayList<String> = arrayListOf(
                "fixedLeg.notional.pennies",
                "fixedLeg.fixedRate.ratioUnit",
                "fixedLeg.fixedRate.ratioUnit.value",
                "floatingLeg.notional.pennies",
                "fixedLeg.fixedRate",
                "currentBusinessDate",
                "calculation.floatingLegPaymentSchedule.get(currentBusinessDate)",
                "fixedLeg.notional.currency.currencyCode",
                "fixedLeg.notional.pennies * 10",
                "fixedLeg.notional.pennies * fixedLeg.fixedRate.ratioUnit.value",
                "(fixedLeg.notional.currency.currencyCode.equals('GBP')) ? 365 : 360 ",
                "(fixedLeg.notional.pennies * (fixedLeg.fixedRate.ratioUnit.value))"
                // "calculation.floatingLegPaymentSchedule.get(context.getDate('currentDate')).rate"
                // "calculation.floatingLegPaymentSchedule.get(context.getDate('currentDate')).rate.ratioUnit.value",
                //"( fixedLeg.notional.pennies * (fixedLeg.fixedRate.ratioUnit.value)) - (floatingLeg.notional.pennies * (calculation.fixingSchedule.get(context.getDate('currentDate')).rate.ratioUnit.value))",
                // "( fixedLeg.notional * fixedLeg.fixedRate )"
        )

        for (i in stuffToPrint) {
            println(i)
            var z = dummyIRS.evaluateCalculation(LocalDate.of(2016, 9, 12), Expression(i))
            println(z.javaClass)
            println(z)
            println("-----------")
        }
        // This does not throw an exception in the test itself; it evaluates the above and they will throw if they do not pass.
    }


    fun trade(): TransactionGroupDSL<InterestRateSwap.State> {
        val txgroup: TransactionGroupDSL<InterestRateSwap.State> = transactionGroupFor() {
            transaction("Agreement") {
                output("irs post agreement") { singleIRS() }
                arg(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
                timestamp(TEST_TX_TIME)
            }

            transaction("Fix") {
                input("irs post agreement")
                output("irs post first fixing") { "irs post agreement".output }
                arg(ORACLE_PUBKEY) { InterestRateSwap.Commands.Fix() }
                timestamp(TEST_TX_TIME)
            }

            transaction("Pay") {
                input("irs post first fixing")
                output("irs post first payment") { "irs post first fixing".output }
                arg(MEGA_CORP_PUBKEY, MINI_CORP_PUBKEY) { InterestRateSwap.Commands.Pay() }
                timestamp(TEST_TX_TIME)
            }
        }
        return txgroup
    }
}