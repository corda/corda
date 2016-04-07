/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package contracts

fun InterestRateSwap.State.exportIRSToCSV(): String =
        "Fixed Leg\n" + FixedRatePaymentEvent.CSVHeader + "\n" +
                this.calculation.fixedLegPaymentSchedule.toSortedMap().values.map { it.asCSV() }.joinToString("\n") + "\n" +
                "Floating Leg\n" + FloatingRatePaymentEvent.CSVHeader + "\n" +
                this.calculation.floatingLegPaymentSchedule.toSortedMap().values.map { it.asCSV() }.joinToString("\n") + "\n"
