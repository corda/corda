package net.corda.plugin

import net.corda.core.node.CordaPluginRegistry
import net.corda.core.schemas.MappedSchema
import net.corda.schemas.CashSchemaV1

class FinancePluginRegistry : CordaPluginRegistry() {
    override val requiredSchemas: Set<MappedSchema> = setOf(
            CashSchemaV1
    )
}