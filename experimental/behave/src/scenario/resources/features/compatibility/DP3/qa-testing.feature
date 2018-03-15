@compatibility @node
Feature: Compatibility - Node versions
  To support an interoperable Corda network, a Corda (OS) node must have the ability to transact with an R3 Corda (Enterprise) node for the same version.

  Scenario Outline: Corda (OS) Node can transact with R3 Corda (Enterprise) node, in an R3 Corda configured network.
    Given a node PartyA of version <R3-Corda-Node-Version> with proxy
    And node PartyA has the finance app installed
    And a node PartyB of version <Corda-Node-Version>
    And node PartyB has the finance app installed
    And a nonvalidating notary Notary of version <R3-Corda-Node-Version>
    When the network is ready
    Then node PartyA can issue 1000 <Currency>
    And node PartyA can transfer 100 <Currency> to node B

    Examples:
      | Corda-Node-Version | R3-Corda-Node-Version   | Currency |
      | corda-3.0          | r3-master               | GBP      |
#      | corda-3.0          | r3corda-3.0-DP3         | GBP      |