package com.r3corda.contracts.universal

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
fun liableParties(contract: Arrangement) : Set<PublicKey> {

    fun visit(arrangement: Arrangement) : ImmutableSet<PublicKey> {
        when (arrangement) {
            is Zero -> return ImmutableSet.of<PublicKey>()
            is Transfer -> return ImmutableSet.of(arrangement.from.owningKey)
            is Action ->
                if (arrangement.actors.size != 1)
                    return visit(arrangement.arrangement)
                else
                    return Sets.difference(visit(arrangement.arrangement), ImmutableSet.of(arrangement.actors.single())).immutableCopy()
            is And ->
                return arrangement.arrangements.fold( ImmutableSet.builder<PublicKey>(), { builder, k -> builder.addAll( visit(k)) } ).build()
            is Or ->
                return arrangement.actions.fold( ImmutableSet.builder<PublicKey>(), { builder, k -> builder.addAll( visit(k)) } ).build()
        }

        throw IllegalArgumentException()
    }

    return visit(contract)
}

/** returns list of involved parties for a given contract */
fun involvedParties(arrangement: Arrangement) : Set<PublicKey> {

    fun visit(arrangement: Arrangement) : ImmutableSet<PublicKey> {
        return when (arrangement) {
            is Zero -> ImmutableSet.of<PublicKey>()
            is Transfer -> ImmutableSet.of(arrangement.from.owningKey)
            is Action -> Sets.union( visit(arrangement.arrangement), arrangement.actors.map { it.owningKey }.toSet() ).immutableCopy()
            is And ->
                arrangement.arrangements.fold( ImmutableSet.builder<PublicKey>(), { builder, k -> builder.addAll( visit(k)) } ).build()
            is Or ->
                arrangement.actions.fold( ImmutableSet.builder<PublicKey>(), { builder, k -> builder.addAll( visit(k)) } ).build()
            else -> throw IllegalArgumentException()
        }
    }

    return visit(arrangement)
}

fun replaceParty(action: Action, from: Party, to: Party) : Action {
    if (action.actors.contains(from)) {
        return Action( action.name, action.condition, action.actors - from + to, replaceParty(action.arrangement, from, to))
    }
    return Action( action.name, action.condition, action.actors, replaceParty(action.arrangement, from, to))
}

fun replaceParty(arrangement: Arrangement, from: Party, to: Party) : Arrangement {
    return when (arrangement) {
        is Zero -> arrangement
        is Transfer -> Transfer( arrangement.amount, arrangement.currency,
                                 if (arrangement.from == from) to else arrangement.from,
                                 if (arrangement.to == from) to else arrangement.to )
        is Action -> replaceParty(arrangement, from, to)
        is And -> And( arrangement.arrangements.map { replaceParty(it, from, to) }.toSet() )
        is Or -> Or( arrangement.actions.map { replaceParty(it, from, to) }.toSet() )
        else -> throw IllegalArgumentException()
    }
}

fun actions(arrangement: Arrangement) : Map<String, Action> {

    when (arrangement) {
        is Zero -> return mapOf()
        is Transfer -> return mapOf()
        is Action -> return mapOf( arrangement.name to arrangement)
        is Or -> return arrangement.actions.map { it.name to it }.toMap()
    }

    throw IllegalArgumentException()
}