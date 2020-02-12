package net.corda.detekt.plugins

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider
import net.corda.detekt.plugins.rules.UntimedTest

class CordaDetektProvider : RuleSetProvider {

    override val ruleSetId: String = "corda-detekt"

    override fun instance(config: Config): RuleSet = RuleSet(
            ruleSetId,
            listOf(
                UntimedTest()
            )
    )
}