# Generic contracts
 
This is a demonstration of how to build generic contracts or higher order contracts on top of Corda.

## Observables

An observable is a state that can be observed and measured at a given time. Examples of observables could be Libor interest rate, default of a company or an FX fixing.

An observable has a underlying type - a fixing will be a numeric type, whereas default status for a company may be a boolean value.

Observables can be based on time. A typical boolean observable on time could be ``After('2017-03-01')`` which is true only if time is after 1st of March 2017.

Simple expressions on observables can be formed. For example ``EURUSD > 1.2``is a boolean observable, whereas the EURUSD fixing itself is a numeric observable.


## Building blocks

##### ``Zero``
A base contract with no rights and no obligations. Contract cancellation/termination is a transition to ``Zero``.

##### ``Transfer amount, currency, fromParty, toParty``
A base contract representing immediate transfer of Cash - X amount of currency CCY from party A to party B. X is an observable of type BigDecimal.

##### ``And contract1 ... contractN``
A combinator over a list of contracts. Each contract in list will create a separate independent contract state. The ``And`` combinator cannot be root in a contract.

##### ``Action name, condition, actors, contract``
An action combinator. This declares a named action that can be taken by anyone of the actors given that _condition_ is met. If the action is performed the contract state transitions into the specificed contract.

##### ``Or action1 ... actionN``
A combinator that can only be used on action contracts. This means only one of the action can be executed. Should any one action be executed, all other actions are discarded.

#### No schedulers
The ``Action`` combinator removes the need for an integral scheduler. The responsibility for triggering an event always with the beneficiary. The beneficiary may want a scheduler for making sure fixing and other events are taken advantages of, but it would be an optional additional layer.

#### Examples

##### CDS contract
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


##### FX call option
Example of a european FX vanilla call option:
```
val my_fx_option =

        (roadRunner).may {
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

### TODO

-  Fixings and other state variables