package net.corda.core.internal

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.ContractClassName

/** Indicates that this transaction replaces the inputs contract state to another contract state */
data class UpgradeCommand(val upgradedContractClass: ContractClassName) : CommandData