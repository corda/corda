@compatibility @cordapps
Feature: Compatibility - CorDapp versions
  To support an interoperable Corda network, a Corda node must have the ability to transact with another Corda node
  when each node has a different versions of the same cordapp but identical Flow interfaces and Contract usage.

  Scenario Outline: Corda node can transact with another Corda node where each has a different Cordapp versions but with same Flows, Contracts, Contract States, Contract State Schemas
    Given a node A of version <Corda-Node-Version-X> with proxy
    And node A has <Cordapp-Version-X> finance app installed
    And a node B of version <Corda-Node-Version-X>
    And node B has <Cordapp-Version-Y> finance app installed
    When the network is ready
    Then node A can transfer 100 tokens to node B

  Examples:
      | Corda-Node-Version-X | Cordapp-Version-X | Cordapp-Version-Y |
      | corda-master         | finance-V1        | finance-V2        |
      | r3-master            | finance-V1        | finance-V2        |

  Scenario Outline: Scenarios that exercise both happy and unhappy paths using Contract Constraints checking (hash, CZ whitelist, signature).
    Examples: TODO