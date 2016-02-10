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

fun getKotlinCommercialPaper() : ICommercialPaperState {
    return CommercialPaper.State(
            issuance = MEGA_CORP.ref(123),
            owner = MEGA_CORP_PUBKEY,
            faceValue = 1000.DOLLARS,
            maturityDate = TEST_TX_TIME + 7.days
    )
}

open class KotlinCommercialPaperTest() : ICommercialPaperTestTemplate {
    override fun getPaper() : ICommercialPaperState = getKotlinCommercialPaper()
    override fun getIssueCommand() : CommandData = CommercialPaper.Commands.Issue()
    override fun getRedeemCommand() : CommandData = CommercialPaper.Commands.Redeem()
    override fun getMoveCommand() : CommandData = CommercialPaper.Commands.Move()
}

class CommercialPaperTestsKotlin() : CommercialPaperTestsGeneric( KotlinCommercialPaperTest()) { }

fun main(args: Array<String>) {
    CommercialPaperTestsKotlin().trade().visualise()
}

