package net.corda.core.schemas.requery.converters

import io.requery.Converter
import io.requery.converter.EnumOrdinalConverter
import io.requery.sql.Mapping
import net.corda.core.contracts.ContractState
import net.corda.core.node.services.Vault

import java.sql.*
import java.time.*

/**
 * Converter which persists a [Vault.StateStatus] enum using its enum ordinal representation
 */
class VaultStateStatusConverter() : EnumOrdinalConverter<Vault.StateStatus>(Vault.StateStatus::class.java)
