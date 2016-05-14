package contracts

import core.*
import core.contracts.*
import core.testutils.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

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

            InterestRateSwap.State(fixedLeg = fixedLeg, floatingLeg = floatingLeg, calculation = calculation, common = common, notary = DUMMY_NOTARY)
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
                    tradeID = "trade2",
                    hashLegalDocs = "put hash here",
                    dailyInterestAmount = Expression("(CashAmount * InterestRate ) / (fixedLeg.notional.currency.currencyCode.equals('GBP')) ? 365 : 360")
            )

            return InterestRateSwap.State(fixedLeg = fixedLeg, floatingLeg = floatingLeg, calculation = calculation, common = common, notary = DUMMY_NOTARY)

        }
        else -> TODO("IRS number $irsSelect not defined")
    }
}

class IRSTests {

    val attachments = MockStorageService().attachments

    val exampleIRS = createDummyIRS(1)

    val inState = InterestRateSwap.State(
            exampleIRS.fixedLeg,
            exampleIRS.floatingLeg,
            exampleIRS.calculation,
            exampleIRS.common,
            DUMMY_NOTARY
    )

    val outState = inState.copy()

    @Test
    fun ok() {
        trade().verify()
    }

    @Test
    fun `ok with groups`() {
        tradegroups().verify()
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
                    common = dummyIRS.common,
                    notary = DUMMY_NOTARY).apply {
                setTime(TEST_TX_TIME, DUMMY_NOTARY, 30.seconds)
                signWith(MEGA_CORP_KEY)
                signWith(MINI_CORP_KEY)
                signWith(DUMMY_NOTARY_KEY)
            }
            gtx.toSignedTransaction().verifyToLedgerTransaction(MockIdentityService, attachments)
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
     * Test the generate. No explicit exception as if something goes wrong, we'll find out anyway.
     */
    @Test
    fun generateIRS() {
        // Tests aren't allowed to return things
        generateIRSTxn(1)
    }

    /**
     * Testing a simple IRS, add a few fixings and then display as CSV
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

        for (it in fixings) {
            newCalculation = newCalculation.applyFixing(it.key, FixedRate(PercentageRatioUnit(it.value)))
        }

        val newIRS = InterestRateSwap.State(irs.fixedLeg, irs.floatingLeg, newCalculation, irs.common, DUMMY_NOTARY)
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
                    setTime(TEST_TX_TIME, DUMMY_NOTARY, 30.seconds)
                    signWith(MEGA_CORP_KEY)
                    signWith(MINI_CORP_KEY)
                    signWith(DUMMY_NOTARY_KEY)
                }
                tx.toSignedTransaction().verifyToLedgerTransaction(MockIdentityService, attachments)
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
    fun `expression calculation testing`() {
        val dummyIRS = singleIRS()
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


    /**
     * Generates a typical transactional history for an IRS.
     */
    fun trade(): TransactionGroupDSL<InterestRateSwap.State> {

        val ld = LocalDate.of(2016, 3, 8)
        val bd = BigDecimal("0.0063518")

        val txgroup: TransactionGroupDSL<InterestRateSwap.State> = transactionGroupFor() {
            transaction("Agreement") {
                output("irs post agreement") { singleIRS() }
                arg(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
                timestamp(TEST_TX_TIME)
            }

            transaction("Fix") {
                input("irs post agreement")
                output("irs post first fixing") {
                    "irs post agreement".output.copy(
                            "irs post agreement".output.fixedLeg,
                            "irs post agreement".output.floatingLeg,
                            "irs post agreement".output.calculation.applyFixing(ld, FixedRate(RatioUnit(bd))),
                            "irs post agreement".output.common
                    )
                }
                arg(ORACLE_PUBKEY) {
                    InterestRateSwap.Commands.Fix()
                }
                arg(ORACLE_PUBKEY) {
                    Fix(FixOf("ICE LIBOR", ld, Tenor("3M")), bd)
                }
                timestamp(TEST_TX_TIME)
            }
        }
        return txgroup
    }

    @Test
    fun `ensure failure occurs when there are inbound states for an agreement command`() {
        transaction {
            input() { singleIRS() }
            output("irs post agreement") { singleIRS() }
            arg(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "There are no in states for an agreement"
        }
    }

    @Test
    fun `ensure failure occurs when no events in fix schedule`() {
        val irs = singleIRS()
        val emptySchedule = HashMap<LocalDate, FixedRatePaymentEvent>()
        transaction {
            output() {
                irs.copy(calculation = irs.calculation.copy(fixedLegPaymentSchedule = emptySchedule))
            }
            arg(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "There are events in the fix schedule"
        }
    }

    @Test
    fun `ensure failure occurs when no events in floating schedule`() {
        val irs = singleIRS()
        val emptySchedule = HashMap<LocalDate, FloatingRatePaymentEvent>()
        transaction {
            output() {
                irs.copy(calculation = irs.calculation.copy(floatingLegPaymentSchedule = emptySchedule))
            }
            arg(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "There are events in the float schedule"
        }
    }

    @Test
    fun `ensure notionals are non zero`() {
        val irs = singleIRS()
        transaction {
            output() {
                irs.copy(irs.fixedLeg.copy(notional = irs.fixedLeg.notional.copy(pennies = 0)))
            }
            arg(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "All notionals must be non zero"
        }

        transaction {
            output() {
                irs.copy(irs.fixedLeg.copy(notional = irs.floatingLeg.notional.copy(pennies = 0)))
            }
            arg(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "All notionals must be non zero"
        }
    }

    @Test
    fun `ensure positive rate on fixed leg`() {
        val irs = singleIRS()
        val modifiedIRS = irs.copy(fixedLeg = irs.fixedLeg.copy(fixedRate = FixedRate(PercentageRatioUnit("-0.1"))))
        transaction {
            output() {
                modifiedIRS
            }
            arg(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "The fixed leg rate must be positive"
        }
    }

    /**
     * This will be modified once we adapt the IRS to be cross currency
     */
    @Test
    fun `ensure same currency notionals`() {
        val irs = singleIRS()
        val modifiedIRS = irs.copy(fixedLeg = irs.fixedLeg.copy(notional = Amount(irs.fixedLeg.notional.pennies, Currency.getInstance("JPY"))))
        transaction {
            output() {
                modifiedIRS
            }
            arg(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "The currency of the notionals must be the same"
        }
    }

    @Test
    fun `ensure notional amounts are equal`() {
        val irs = singleIRS()
        val modifiedIRS = irs.copy(fixedLeg = irs.fixedLeg.copy(notional = Amount(irs.floatingLeg.notional.pennies + 1, irs.floatingLeg.notional.currency)))
        transaction {
            output() {
                modifiedIRS
            }
            arg(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "All leg notionals must be the same"
        }
    }

    @Test
    fun `ensure trade date and termination date checks are done pt1`() {
        val irs = singleIRS()
        val modifiedIRS1 = irs.copy(fixedLeg = irs.fixedLeg.copy(terminationDate = irs.fixedLeg.effectiveDate.minusDays(1)))
        transaction {
            output() {
                modifiedIRS1
            }
            arg(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "The effective date is before the termination date for the fixed leg"
        }

        val modifiedIRS2 = irs.copy(floatingLeg = irs.floatingLeg.copy(terminationDate = irs.floatingLeg.effectiveDate.minusDays(1)))
        transaction {
            output() {
                modifiedIRS2
            }
            arg(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "The effective date is before the termination date for the floating leg"
        }
    }

    @Test
    fun `ensure trade date and termination date checks are done pt2`() {
        val irs = singleIRS()

        val modifiedIRS3 = irs.copy(floatingLeg = irs.floatingLeg.copy(terminationDate = irs.fixedLeg.terminationDate.minusDays(1)))
        transaction {
            output() {
                modifiedIRS3
            }
            arg(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "The termination dates are aligned"
        }


        val modifiedIRS4 = irs.copy(floatingLeg = irs.floatingLeg.copy(effectiveDate = irs.fixedLeg.effectiveDate.minusDays(1)))
        transaction {
            output() {
                modifiedIRS4
            }
            arg(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
            timestamp(TEST_TX_TIME)
            this `fails requirement` "The effective dates are aligned"
        }
    }


    @Test
    fun `various fixing tests`() {

        val ld = LocalDate.of(2016, 3, 8)
        val bd = BigDecimal("0.0063518")

        transaction {
            output("irs post agreement") { singleIRS() }
            arg(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
            timestamp(TEST_TX_TIME)
        }

        val oldIRS = singleIRS(1)
        val newIRS = oldIRS.copy(oldIRS.fixedLeg,
                oldIRS.floatingLeg,
                oldIRS.calculation.applyFixing(ld, FixedRate(RatioUnit(bd))),
                oldIRS.common)

        transaction {
            input() {
                oldIRS

            }

            // Templated tweak for reference. A corrent fixing applied should be ok
            tweak {
                arg(ORACLE_PUBKEY) {
                    InterestRateSwap.Commands.Fix()
                }
                timestamp(TEST_TX_TIME)
                arg(ORACLE_PUBKEY) {
                    Fix(FixOf("ICE LIBOR", ld, Tenor("3M")), bd)
                }
                output() { newIRS }
                this.accepts()
            }

            // This test makes sure that verify confirms the fixing was applied and there is a difference in the old and new
            tweak {
                arg(ORACLE_PUBKEY) { InterestRateSwap.Commands.Fix() }
                timestamp(TEST_TX_TIME)
                arg(ORACLE_PUBKEY) { Fix(FixOf("ICE LIBOR", ld, Tenor("3M")), bd) }
                output() { oldIRS }
                this`fails requirement` "There is at least one difference in the IRS floating leg payment schedules"
            }

            // This tests tries to sneak in a change to another fixing (which may or may not be the latest one)
            tweak {
                arg(ORACLE_PUBKEY) { InterestRateSwap.Commands.Fix() }
                timestamp(TEST_TX_TIME)
                arg(ORACLE_PUBKEY) {
                    Fix(FixOf("ICE LIBOR", ld, Tenor("3M")), bd)
                }

                val firstResetKey = newIRS.calculation.floatingLegPaymentSchedule.keys.first()
                val firstResetValue = newIRS.calculation.floatingLegPaymentSchedule[firstResetKey]
                var modifiedFirstResetValue = firstResetValue!!.copy(notional = Amount(firstResetValue.notional.pennies, Currency.getInstance("JPY")))

                output() {
                    newIRS.copy(
                            newIRS.fixedLeg,
                            newIRS.floatingLeg,
                            newIRS.calculation.copy(floatingLegPaymentSchedule = newIRS.calculation.floatingLegPaymentSchedule.plus(
                                    Pair(firstResetKey, modifiedFirstResetValue))),
                            newIRS.common
                    )
                }
                this`fails requirement` "There is only one change in the IRS floating leg payment schedule"
            }

            // This tests modifies the payment currency for the fixing
            tweak {
                arg(ORACLE_PUBKEY) { InterestRateSwap.Commands.Fix() }
                timestamp(TEST_TX_TIME)
                arg(ORACLE_PUBKEY) { Fix(FixOf("ICE LIBOR", ld, Tenor("3M")), bd) }

                val latestReset = newIRS.calculation.floatingLegPaymentSchedule.filter { it.value.rate is FixedRate }.maxBy { it.key }
                var modifiedLatestResetValue = latestReset!!.value.copy(notional = Amount(latestReset.value.notional.pennies, Currency.getInstance("JPY")))

                output() {
                    newIRS.copy(
                            newIRS.fixedLeg,
                            newIRS.floatingLeg,
                            newIRS.calculation.copy(floatingLegPaymentSchedule = newIRS.calculation.floatingLegPaymentSchedule.plus(
                                    Pair(latestReset.key, modifiedLatestResetValue))),
                            newIRS.common
                    )
                }
                this`fails requirement` "The fix payment has the same currency as the notional"
            }
        }
    }


    /**
     * This returns an example of transactions that are grouped by TradeId and then a fixing applied.
     * It's important to make the tradeID different for two reasons, the hashes will be the same and all sorts of confusion will
     * result and the grouping won't work either.
     * In reality, the only fields that should be in common will be the next fixing date and the reference rate.
     */
    fun tradegroups(): TransactionGroupDSL<InterestRateSwap.State> {
        val ld1 = LocalDate.of(2016, 3, 8)
        val bd1 = BigDecimal("0.0063518")

        val irs = singleIRS()

        val txgroup: TransactionGroupDSL<InterestRateSwap.State> = transactionGroupFor() {
            transaction("Agreement") {
                output("irs post agreement1") {
                    irs.copy(
                            irs.fixedLeg,
                            irs.floatingLeg,
                            irs.calculation,
                            irs.common.copy(tradeID = "t1")

                    )
                }
                arg(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
                timestamp(TEST_TX_TIME)
            }

            transaction("Agreement") {
                output("irs post agreement2") {
                    irs.copy(
                            irs.fixedLeg,
                            irs.floatingLeg,
                            irs.calculation,
                            irs.common.copy(tradeID = "t2")

                    )
                }
                arg(MEGA_CORP_PUBKEY) { InterestRateSwap.Commands.Agree() }
                timestamp(TEST_TX_TIME)
            }

            transaction("Fix") {
                input("irs post agreement1")
                input("irs post agreement2")
                output("irs post first fixing1") {
                    "irs post agreement1".output.copy(
                            "irs post agreement1".output.fixedLeg,
                            "irs post agreement1".output.floatingLeg,
                            "irs post agreement1".output.calculation.applyFixing(ld1, FixedRate(RatioUnit(bd1))),
                            "irs post agreement1".output.common.copy(tradeID = "t1")
                    )
                }
                output("irs post first fixing2") {
                    "irs post agreement2".output.copy(
                            "irs post agreement2".output.fixedLeg,
                            "irs post agreement2".output.floatingLeg,
                            "irs post agreement2".output.calculation.applyFixing(ld1, FixedRate(RatioUnit(bd1))),
                            "irs post agreement2".output.common.copy(tradeID = "t2")
                    )
                }

                arg(ORACLE_PUBKEY) {
                    InterestRateSwap.Commands.Fix()
                }
                arg(ORACLE_PUBKEY) {
                    Fix(FixOf("ICE LIBOR", ld1, Tenor("3M")), bd1)
                }
                timestamp(TEST_TX_TIME)
            }
        }
        return txgroup
    }
}


