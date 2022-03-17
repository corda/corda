package net.corda.finance.contracts.asset

import net.corda.core.contracts.CommandData

/**
 * Allows new cash states to be issued into existence.
 */
data class Issue(val issueId: String) : CommandData