/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package contracts

import core.CommandData
import core.DOLLARS
import core.days
import core.testutils.MEGA_CORP
import core.testutils.MEGA_CORP_PUBKEY
import core.testutils.TEST_TX_TIME

fun getJavaCommericalPaper() : ICommercialPaperState {
    return JavaCommercialPaper.State(
            MEGA_CORP.ref(123),
            MEGA_CORP_PUBKEY,
            1000.DOLLARS,
            TEST_TX_TIME + 7.days
    )
}

open class JavaCommercialPaperTest() : ICommercialPaperTestTemplate {
    override fun getPaper() : ICommercialPaperState = getJavaCommericalPaper()
    override fun getIssueCommand() : CommandData = JavaCommercialPaper.Commands.Issue()
    override fun getRedeemCommand() : CommandData = JavaCommercialPaper.Commands.Redeem()
    override fun getMoveCommand() : CommandData = JavaCommercialPaper.Commands.Move()
}

class CommercialPaperTestsJava() : CommercialPaperTestsGeneric(JavaCommercialPaperTest()) { }


fun main(args: Array<String>) {
    CommercialPaperTestsJava().trade().visualise()
}
