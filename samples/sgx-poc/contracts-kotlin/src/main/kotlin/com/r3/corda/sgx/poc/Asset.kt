package com.r3.corda.sgx.poc.contracts

import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

@BelongsToContract(AssetContract::class)
data class Asset(val id: Int,
                 val owner: Party,
                 val issuer: Party): ContractState {
    override val participants: List<AbstractParty> get() = listOf(owner)
}
