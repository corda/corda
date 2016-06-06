package com.r3corda.contracts.generic

import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets
import com.r3corda.core.contracts.Amount
import com.r3corda.core.crypto.Party
import java.security.PublicKey
import java.util.*

/**
 * Created by sofusmortensen on 23/05/16.
 */


open class Kontract {

    class Zero : Kontract()

    // should be replaced with something that uses Corda assets and/or cash
    class Transfer(val amount: Observable<Long>, val currency: Currency, val from: Party, val to: Party) : Kontract() {
        constructor(amount: Amount<Currency>, from: Party, to: Party ) : this(const(amount.pennies), amount.token, from, to)
    }

    class And(val kontracts: Array<Kontract>) : Kontract()


    //
    class Action(val name: String, val condition: Observable<Boolean>, val actors: Array<Party>, val kontract: Kontract) : Kontract() {
        constructor(name: String, condition: Observable<Boolean>, actor: Party, kontract: Kontract) : this(name, condition, arrayOf(actor), kontract)
    }

    // only actions can be or'ed together
    class Or(val contracts: Array<Action>) : Kontract()
}

/** returns list of involved parties for a given contract */
fun liableParties(contract: Kontract) : Set<PublicKey> {

    fun visit(contract: Kontract) : ImmutableSet<PublicKey> {
        when (contract) {
            is Kontract.Zero -> return ImmutableSet.of<PublicKey>()
            is Kontract.Transfer -> return ImmutableSet.of(contract.from.owningKey)
            is Kontract.Action ->
                if (contract.actors.size != 1)
                    return visit(contract.kontract)
                else
                    return Sets.difference(visit(contract.kontract), ImmutableSet.of(contract.actors.single())).immutableCopy()
            is Kontract.And -> return contract.kontracts.fold( ImmutableSet.builder<PublicKey>(), { builder, k -> builder.addAll( visit(k)) } ).build()
            is Kontract.Or -> return contract.contracts.fold( ImmutableSet.builder<PublicKey>(), { builder, k -> builder.addAll( visit(k)) } ).build()
        }

        throw IllegalArgumentException()
    }

    return visit(contract);
}

fun actions(contract: Kontract) : Map<String, Set<PublicKey>> {

    when (contract) {
        is Kontract.Zero -> return mapOf()
        is Kontract.Transfer -> return mapOf()
        is Kontract.Action -> return mapOf( contract.name to contract.actors.map { it.owningKey }.toSet() )
        is Kontract.Or -> {
            val xx = contract.contracts.map { it.name to it.actors.map { it.owningKey }.toSet() }.toMap()
            return xx
        }
    }

    throw IllegalArgumentException()
}