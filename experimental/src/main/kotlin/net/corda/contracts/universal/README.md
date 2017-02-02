# Universal contracts
 
This is a demonstration of how to build a universal contract or higher order contracts on top of Corda. Think of the universal contract as a generalized Ricardian contract where a meta language is used as contract parameter making it possible for a single smart contract type to span a very large family of contracts.

This experimental module is maintained by Sofus Mortensen (sofus.mortensen@nordea.com) of Nordea.

## Overview

### Motivation and Layers of smart contracts
Currently, in Corda, when discussing smart contracts we have two levels of contracts. At the lowest layer we have the _Corda smart contracts_ represented by JVM bytecode. At the highest level we have Ricardian contract like the _Smart Contract Templates_ where a contract is created by picking an existing template and filling in the required parameters. The latter kind are suitable for non-developer end users.

At the highest level in order to support a new kind of contract, a novel new contract type might be required to be developed at the lowest level. Currently a lot of work is needed to write a smart contract at this level, which obviously takes time to write but more importantly takes considerable time to review and verify (which contract participant should do). There is a significant operation risk associated with contract types. Having re-usable components will arguably reduce development time and associated risk.

What is proposed here is an intermediate layer in between by creating a highly customizable smart contract covering a large family of OTC contracts by having a simple yet expressive representation of contract semantics in the contract state. The objectives are:

 - writing a new contract requires only lines of codee rather than pages of code.
 - non-developers should be able able of reading and possibly writing smart contracts.
 - the contract format should be suitable for automatic transformation and inspection.

The last point is important because banks will need to integrate smart contract into their existing systems. Most banks already have _script_ representation of trades in order to have somewhat generic pricing and risk infrastructure.

### Inspiration
The representation is inspired by _composing contracts_ by Simon Peyton Jones, Jean-Marc Eber and Julian Seward. The two most important differences from _composing contracts_ are:

 - No implicit contract holder and writer. A contract can have an arbitrary number of parties (although less than two does not make sense).
 - Handling and timing of an event is a responsibility of the beneficiary of the event.

## Components
### Perceivables

A perceivable is a state that can be perceived and measured at a given time. Examples of perceivables could be LIBOR interest rate, default of a company or an FX fixing.

A perceivable has a underlying type - a fixing will be a numeric type, whereas default status for a company may be a boolean value.

Perceivables can be based on time. A typical boolean perceivable on time could be ``After('2017-03-01')`` which is true only if time is after 1st of March 2017.

Simple expressions on perceivables can be formed. For example ``EURUSD > 1.2``is a boolean perceivable, whereas the EURUSD fixing itself is a numeric perceivable.

### Building blocks

#### ``Zero``
A base contract with no rights and no obligations. Contract cancellation/termination is a transition to ``Zero``.

#### ``Obligation amount, currency, fromParty, toParty``
A base contract representing debt of X amount of currency CCY from party A to party B. X is an observable of type BigDecimal. 

#### ``And contract1 ... contractN``
A combinator over a list of contracts. Each contract in list will create a separate independent contract state. The ``And`` combinator cannot be root in a contract.

#### ``Action [name, condition, contract]``
An action combinator. This declares a list of named actions, only one can be taken and only if the condition is satisfied. If the action is performed the contract state transitions into the specificed contract.

#### ``RollOut startDate endDate frequency contractTemplate``
A combinator for rolling out a date sequence using specified template

#### ``Continuation``
Marks point of recursion for the `RollOut` combinator.

### Comments

## No schedulers
The ``Action`` combinator removes the need for an integral scheduler. The responsibility for triggering an event always with the beneficiary. The beneficiary may want a scheduler for making sure fixing and other events are taken advantages of, but it would be an optional additional layer.

## Examples

### Zero coupon bond
Example of a zero coupon bond:
```
    val zero_coupon_bond =

            (roadRunner or wileECoyote).may {
                "execute".givenThat(after("01/09/2017")) {
                    wileECoyote.gives(roadRunner, 100.K*GBP)
                }
            }
```

Tag represensation of above:
```
    <action>
        <actor>
            <party ref="road runner"/>
            <party ref="wile e coyote"/>
        </actor>
        <condition>
            <after date="01/09/2017"/>
        </condition>
        <contract>
            <obligation>
                <from><party ref="wile e coyote"/></from>
                <to><party ref="road runner"/></to>
                <asset>
                    <cash>
                        <amount>100000</amount>
                        <currency>USD</currency>
                    </cash>
                </asset>
            </obligation>
        </contract>
    </action>
```

### CDS contract
Simple example of a credit default swap written by 'Wile E Coyote' paying 1,000,000 USD to beneficiary 'Road Runner' in the event of a default of 'ACME Corporation'.

```
val my_cds_contract =

        roadRunner.may {
            "exercise".givenThat(acmeCorporationHasDefaulted) {
                wileECoyote.gives(roadRunner, 1.M*USD)
            }
        } or (roadRunner or wileECoyote).may {
            "expire".givenThat(after("2017-09-01")) {}
        }
```

The logic says that party 'Road Runner' may 'exercise' if and only if 'ACME Corporation' has defaulted. Party 'Wile E Coyote' may expire the contract in the event that expiration date has been reached (and contract has not been
exercised).

Note that it is always the task of the beneficiary of an event to trigger the event. This way a scheduler is not needed as a core component of Corda (but may be a convenient addition on top of Corda).

### FX call option
Example of a european FX vanilla call option:
```
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
```

There are two actors. The contract holder _exercise_ at anytime, resulting in the contract being transformed into an FX swap contract, where both parties at anytime after the delivery date can trigger cash flow exchange. The writer of the contract can anytime after maturity _expire_ the contract effectively transforming the contract into void. Notice again that all scheduling is left to the parties of the contract.

## TODO

- Fixings and other state variables

- Date shift, date rolling, according to holiday calendar

- Underlying conventions for contracts (important to avoid cluttering)

- For convenience - automatic roll out of date sequences - [in progress]

- Think about how to handle classic FX barrier events. Maybe an Oracle can issue proof of an event? Would there be a problem if beneficiary did not raise the event immediately?

## Questions

- How to integrate with Cash on ledger, or more generally assets on ledger?

- For integration with other contracts (Cash and Assets in general), I suspect changes need to be made to those contracts. Ie. how can you create the transaction in future without requiring signature of the payer?

- Discuss Oracle. How to add proof of observable event?