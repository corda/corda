package net.corda.finance.plugin

import net.corda.core.node.CordaPluginRegistry
import net.corda.core.schemas.MappedSchema
import net.corda.finance.schemas.CashSchemaV1

class FinancePluginRegistry : CordaPluginRegistry() {
    override val requiredSchemas: Set<MappedSchema> = setOf(
            CashSchemaV1
    )
}