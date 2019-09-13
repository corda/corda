package net.corda.core.internal.rules

import net.corda.core.contracts.ContractState

// This file provides rules that depend on the targetVersion of the current Contract or Flow.
// In core, this is determined by means which are unavailable in the DJVM,
// so we must provide deterministic alternatives here.

@Suppress("unused")
object StateContractValidationEnforcementRule {
    fun shouldEnforce(@Suppress("UNUSED_PARAMETER") state: ContractState): Boolean = true
}