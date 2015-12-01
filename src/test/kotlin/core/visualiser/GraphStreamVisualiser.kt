/*
 * Copyright 2015, R3 CEV. All rights reserved.
 */
@file:Suppress("CAST_NEVER_SUCCEEDS")

package core.visualiser

import contracts.Cash
import contracts.CommercialPaper
import core.Amount
import core.ContractState
import core.DOLLARS
import core.days
import core.testutils.*
import java.time.Instant
import kotlin.reflect.memberProperties


val PAPER_1 = CommercialPaper.State(
        issuance = MEGA_CORP.ref(123),
        owner = MEGA_CORP_PUBKEY,
        faceValue = 1000.DOLLARS,
        maturityDate = TEST_TX_TIME + 7.days
)

private fun trade(redemptionTime: Instant = TEST_TX_TIME + 8.days,
                  aliceGetsBack: Amount = 1000.DOLLARS,
                  destroyPaperAtRedemption: Boolean = true): TransactionGroupDSL<ContractState> {
    val someProfits = 1200.DOLLARS
    return transactionGroupFor<CommercialPaper.State>() {
        roots {
            transaction(900.DOLLARS.CASH `owned by` ALICE label "alice's $900")
            transaction(someProfits.CASH `owned by` MEGA_CORP_PUBKEY label "some profits")
        }

        // Some CP is issued onto the ledger by MegaCorp.
        transaction("Issuance") {
            output("paper") { PAPER_1 }
            arg(MEGA_CORP_PUBKEY) { CommercialPaper.Commands.Issue() }
        }

        // The CP is sold to alice for her $900, $100 less than the face value. At 10% interest after only 7 days,
        // that sounds a bit too good to be true!
        transaction("Trade") {
            input("paper")
            input("alice's $900")
            output("borrowed $900") { 900.DOLLARS.CASH `owned by` MEGA_CORP_PUBKEY }
            output("alice's paper") { "paper".output `owned by` ALICE }
            arg(ALICE) { Cash.Commands.Move() }
            arg(MEGA_CORP_PUBKEY) { CommercialPaper.Commands.Move() }
        }

        // Time passes, and Alice redeem's her CP for $1000, netting a $100 profit. MegaCorp has received $1200
        // as a single payment from somewhere and uses it to pay Alice off, keeping the remaining $200 as change.
        transaction("Redemption", redemptionTime) {
            input("alice's paper")
            input("some profits")

            output("Alice's profit") { aliceGetsBack.CASH `owned by` ALICE }
            output("Change") { (someProfits - aliceGetsBack).CASH `owned by` MEGA_CORP_PUBKEY }
            if (!destroyPaperAtRedemption)
                output { "paper".output }

            arg(MEGA_CORP_PUBKEY) { Cash.Commands.Move() }
            arg(ALICE) { CommercialPaper.Commands.Redeem() }
        }
    } as TransactionGroupDSL<ContractState>
}


fun main(args: Array<String>) {
    val tg = trade()
    val graph = GraphConverter(tg).convert()
    runGraph(graph, nodeOnClick = { node ->
        val state: ContractState? = node.getAttribute("state")
        if (state != null) {
            val props: List<Pair<String, Any?>> = state.javaClass.kotlin.memberProperties.map { it.name to it.getter.call(state) }
            StateViewer.show(props)
        }
    })
}

