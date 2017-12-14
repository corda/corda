package net.corda.irs.contract

fun InterestRateSwap.State.exportIRSToCSV(): String =
        "Fixed Leg\n" + FixedRatePaymentEvent.CSVHeader + "\n" +
                this.calculation.fixedLegPaymentSchedule.toSortedMap().values.map { it.asCSV() }.joinToString("\n") + "\n" +
                "Floating Leg\n" + FloatingRatePaymentEvent.CSVHeader + "\n" +
                this.calculation.floatingLegPaymentSchedule.toSortedMap().values.map { it.asCSV() }.joinToString("\n") + "\n"
