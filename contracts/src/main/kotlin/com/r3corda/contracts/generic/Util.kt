package com.r3corda.contracts.generic

import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets
import com.r3corda.core.contracts.Amount
import com.r3corda.core.crypto.Party
import java.math.BigDecimal
import java.security.PublicKey
import java.util.*

/**
 * Created by sofusmortensen on 23/05/16.
 */


/** returns list of potentially liable parties for a given contract */
fun liableParties(contract: Kontract) : Set<PublicKey> {

    fun visit(contract: Kontract) : ImmutableSet<PublicKey> {
        when (contract) {
            is Zero -> return ImmutableSet.of<PublicKey>()
            is Transfer -> return ImmutableSet.of(contract.from.owningKey)
            is Action ->
                if (contract.actors.size != 1)
                    return visit(contract.kontract)
                else
                    return Sets.difference(visit(contract.kontract), ImmutableSet.of(contract.actors.single())).immutableCopy()
            is And ->
                return contract.kontracts.fold( ImmutableSet.builder<PublicKey>(), { builder, k -> builder.addAll( visit(k)) } ).build()
            is Or ->
                return contract.actions.fold( ImmutableSet.builder<PublicKey>(), { builder, k -> builder.addAll( visit(k)) } ).build()
        }

        throw IllegalArgumentException()
    }

    return visit(contract);
}

/** returns list of involved parties for a given contract */
fun involvedParties(contract: Kontract) : Set<PublicKey> {

    fun visit(contract: Kontract) : ImmutableSet<PublicKey> {
        return when (contract) {
            is Zero -> ImmutableSet.of<PublicKey>()
            is Transfer -> ImmutableSet.of(contract.from.owningKey)
            is Action -> Sets.union( visit(contract.kontract), contract.actors.map { it.owningKey }.toSet() ).immutableCopy()
            is And ->
                contract.kontracts.fold( ImmutableSet.builder<PublicKey>(), { builder, k -> builder.addAll( visit(k)) } ).build()
            is Or ->
                contract.actions.fold( ImmutableSet.builder<PublicKey>(), { builder, k -> builder.addAll( visit(k)) } ).build()
            else -> throw IllegalArgumentException()
        }
    }

    return visit(contract);
}

fun replaceParty(action: Action, from: Party, to: Party) : Action {
    if (action.actors.contains(from)) {
        return Action( action.name, action.condition, action.actors - from + to, replaceParty(action.kontract, from, to))
    }
    return Action( action.name, action.condition, action.actors, replaceParty(action.kontract, from, to))
}

fun replaceParty(contract: Kontract, from: Party, to: Party) : Kontract {
    return when (contract) {
        is Zero -> contract
        is Transfer -> Transfer( contract.amount, contract.currency,
                                 if (contract.from == from) to else contract.from,
                                 if (contract.to == from) to else contract.to )
        is Action -> replaceParty(contract, from, to)
        is And -> And( contract.kontracts.map { replaceParty(it, from, to) }.toSet() )
        is Or -> Or( contract.actions.map { replaceParty(it, from, to) }.toSet() )
        else -> throw IllegalArgumentException()
    }
}

fun actions(contract: Kontract) : Map<String, Action> {

    when (contract) {
        is Zero -> return mapOf()
        is Transfer -> return mapOf()
        is Action -> return mapOf( contract.name to contract )
        is Or -> return contract.actions.map { it.name to it }.toMap()
    }

    throw IllegalArgumentException()
}