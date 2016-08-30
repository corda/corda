package com.r3corda.client.mock

import com.r3corda.contracts.asset.Cash
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.Party
import com.r3corda.core.serialization.OpaqueBytes
import com.r3corda.core.testing.ledger
import com.r3corda.node.services.monitor.ServiceToClientEvent
import java.time.Instant

/**
 * [Generator]s for incoming/outgoing events to/from the [WalletMonitorService]. Internally it keeps track of owned
 * state/ref pairs, but it doesn't necessarily generate "correct" events!
 */
class EventGenerator(
        val parties: List<Party>,
        val notary: Party
) {

    private var wallet = listOf<StateAndRef<Cash.State>>()

    val issuerGenerator =
            Generator.pickOne(parties).combine(Generator.intRange(0, 1)) { party, ref -> party.ref(ref.toByte()) }

    val currencies = setOf(USD, GBP, CHF).toList() // + Currency.getAvailableCurrencies().toList().subList(0, 3).toSet()).toList()
    val currencyGenerator = Generator.pickOne(currencies)

    val amountIssuedGenerator =
            Generator.intRange(1, 10000).combine(issuerGenerator, currencyGenerator) { amount, issuer, currency ->
                Amount(amount.toLong(), Issued(issuer, currency))
            }

    val publicKeyGenerator = Generator.oneOf(parties.map { it.owningKey })
    val partyGenerator = Generator.oneOf(parties)

    val cashStateGenerator = amountIssuedGenerator.combine(publicKeyGenerator) { amount, from ->
        ledger {
            transaction {
                output("state", Cash.State(amount, from))
                command(amount.token.issuer.party.owningKey, Cash.Commands.Issue())
                verifies()
            }
        }.retrieveOutputStateAndRef(Cash.State::class.java, "state")
    }

    val consumedGenerator: Generator<Set<StateRef>> = Generator.frequency(
            0.7 to Generator.pure(setOf()),
            0.3 to Generator.impure { wallet }.bind { states ->
                Generator.sampleBernoulli(states, 0.2).map { someStates ->
                    val consumedSet = someStates.map { it.ref }.toSet()
                    wallet = wallet.filter { it.ref !in consumedSet }
                    consumedSet
                }
            }
    )
    val producedGenerator: Generator<Set<StateAndRef<ContractState>>> = Generator.frequency(
//            0.1 to Generator.pure(setOf())
            0.9 to Generator.impure { wallet }.bind { states ->
                Generator.replicate(2, cashStateGenerator).map {
                    wallet = states + it
                    it.toSet()
                }
            }
    )

    val outputStateGenerator = consumedGenerator.combine(producedGenerator) { consumed, produced ->
        ServiceToClientEvent.OutputState(Instant.now(), consumed, produced)
    }

    val issueRefGenerator = Generator.intRange(0, 1).map { number -> OpaqueBytes(ByteArray(1, { number.toByte() })) }

    val amountGenerator = Generator.intRange(0, 10000).combine(currencyGenerator) { quantity, currency -> Amount(quantity.toLong(), currency) }

    val issueCashGenerator =
            amountGenerator.combine(partyGenerator, issueRefGenerator) { amount, to, issueRef ->
                ClientToServiceCommand.IssueCash(
                        amount,
                        issueRef,
                        to,
                        notary
                )
            }

    val moveCashGenerator =
            amountIssuedGenerator.combine(
                    partyGenerator
            ) { amountIssued, recipient ->
                ClientToServiceCommand.PayCash(
                        amount = amountIssued,
                        recipient = recipient
                )
            }

    val serviceToClientEventGenerator = Generator.frequency<ServiceToClientEvent>(
            1.0 to outputStateGenerator
    )

    val clientToServiceCommandGenerator = Generator.frequency(
            0.33 to issueCashGenerator,
            0.33 to moveCashGenerator
    )
}
