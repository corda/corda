package net.corda.core.schemas.requery.converters

import io.requery.converter.EnumOrdinalConverter
import net.corda.core.node.services.Vault

/**
 * Converter which persists a [Vault.StateStatus] enum using its enum ordinal representation
 */
class VaultStateStatusConverter : EnumOrdinalConverter<Vault.StateStatus>(Vault.StateStatus::class.java)
