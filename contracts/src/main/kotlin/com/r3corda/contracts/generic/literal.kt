package com.r3corda.contracts.generic

import com.r3corda.core.contracts.Amount
import com.r3corda.core.crypto.Party
import java.util.*

/**
 * Created by sofusmortensen on 23/05/16.
 */

class ContractBuilder {
    val contracts = mutableListOf<Kontract>()

    fun Party.gives(beneficiary: Party, amount: Amount<Currency>) {
        contracts.add( Transfer(amount, this, beneficiary))
    }

    fun final() =
            when (contracts.size) {
                0 -> zero
                1 -> contracts[0]
                else -> And(contracts.toSet())
            }
}

interface GivenThatResolve {
    fun resolve(contract: Kontract)
}

class ActionBuilder(val actors: Array<Party>) {
    val actions = mutableListOf<Action>()

    fun String.givenThat(condition: Observable<Boolean>, init: ContractBuilder.() -> Unit ) {
        val b = ContractBuilder()
        b.init()
        actions.add( Action(this, condition, actors, b.final() ) )
    }

    fun String.givenThat(condition: Observable<Boolean> ) : GivenThatResolve {
        val This = this
        return object : GivenThatResolve {
            override fun resolve(contract: Kontract) {
                actions.add(Action(This, condition, actors, contract))
            }
        }
    }

    fun String.anytime(init: ContractBuilder.() -> Unit ) {
        val b = ContractBuilder()
        b.init()
        actions.add( Action(this, const(true), actors, b.final() ) )
    }
}

fun Party.may(init: ActionBuilder.() -> Unit) : Or {
    val b = ActionBuilder(arrayOf(this))
    b.init()
    return Or(b.actions.toTypedArray())
}

fun Array<Party>.may(init: ActionBuilder.() -> Unit) : Or {
    val b = ActionBuilder(this)
    b.init()
    return Or(b.actions.toTypedArray())
}

infix fun Party.or(party: Party) = arrayOf(this, party)
infix fun Array<Party>.or(party: Party) = this.plus(party)

fun kontract(init: ContractBuilder.() -> Unit ) : Kontract {
    val b = ContractBuilder()
    b.init()
    return b.final();
}

/*
val my_cds_contract =

        roadRunner.may {
            "exercise".givenThat(acmeCorporationHasDefaulted and before("2017-09-01")) {
                wileECoyote.gives(roadRunner, 1.M*USD)
            }
        } or (roadRunner or wileECoyote).may {
            "expire".givenThat(after("2017-09-01")) {}
        }

val my_fx_swap =

        (roadRunner or wileECoyote).may {
            "execute".givenThat(after("2017-09-01")) {
                wileECoyote.gives(roadRunner, 1200.K*USD)
                roadRunner.gives(wileECoyote, 1.M*EUR)
            }
        }

val my_fx_option =

        roadRunner.may {
            "exercise".anytime {
                (roadRunner or wileECoyote).may {
                    "execute".givenThat(after("2017-09-01")) {
                        wileECoyote.gives(roadRunner, 1200.K*USD)
                        roadRunner.gives(wileECoyote, 1.M*EUR)
                    }
                }
            }
        } or wileECoyote.may {
            "expire".givenThat(after("2017-09-01")) {}
        }

val my_fx_knock_out_barrier_option =

        roadRunner.may {
            "exercise".anytime {
                (roadRunner or wileECoyote).may {
                    "execute".givenThat(after("2017-09-01")) {
                        wileECoyote.gives(roadRunner, 1200.K*USD)
                        roadRunner.gives(wileECoyote, 1.M*EUR)
                    }
                }
            }
        } or wileECoyote.may {
            "expire".givenThat(after("2017-09-01")) {}
            "knock out".givenThat( EUR / USD gt 1.3 ) {}
        }

val my_fx_knock_in_barrier_option =

        roadRunner.may {
            "knock in".givenThat(EUR / USD gt 1.3) {
                roadRunner.may {
                    "exercise".anytime {
                        (roadRunner or wileECoyote).may {
                            "execute".givenThat(after("2017-09-01")) {
                                wileECoyote.gives(roadRunner, 1200.K*USD)
                                roadRunner.gives(wileECoyote, 1.M*EUR)
                            }
                        }
                    }
                } or wileECoyote.may {
                    "expire".givenThat(after("2017-09-01")) {}
                }
            }
        } or wileECoyote.may {
            "expire".givenThat(after("2017-09-01")) {}
        }

////

fun fwd(partyA: Party, partyB: Party, maturity: String, contract: Kontract) =
        (partyA or partyB).may {
            "execute".givenThat(after(maturity)).resolve(contract)
        }
*/