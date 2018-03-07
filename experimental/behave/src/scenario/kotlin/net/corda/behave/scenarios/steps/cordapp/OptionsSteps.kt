package net.corda.behave.scenarios.steps.cordapp

import net.corda.behave.scenarios.StepsBlock

fun optionsSteps(steps: StepsBlock) = steps {

    Then<String,Long,String>("^node (\\w+) can self-issue (\\d+) (\\w+)$") { node, amount, currency ->
        options.selfIssueCash(node, amount, currency)
    }

    Then<String,String,Int,String,String,String,String>("^node (\\w+) can issue an option (\\w+) (\\d+) (\\w+) (\\w+) (\\w+) (\\w+)$") {
        node, optionType, strike, currency, expiry, underlying, issuerName ->
        options.issue(node, optionType, strike, currency,
                      expiry.replace("_","-"),
                      underlying.replace("_"," "), issuerName)
    }

    Then<String,String,String>("^node (\\w+) can trade option (\\w+) with node (\\w+)$") { node, tradeId, counterparty ->
        options.trade(node, tradeId, counterparty)
    }
}
