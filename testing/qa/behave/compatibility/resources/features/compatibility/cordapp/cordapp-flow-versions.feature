@compatibility @cordapps @flows
Feature: Compatibility - CorDapp versions
  To support an interoperable Corda network, a Corda node must have the ability to transact with another Corda node
  when each node has a different version of the same cordapp for different Flow change scenarios.

  Scenario Outline: Corda node can transact with another Corda node where each has a Cordapp version with same same Flow version but different implementations
    Given a node A of version <Corda-Node-Version-X>
    And node A has <Cordapp-Version-X> finance app installed
    And a node B of version <Corda-Node-Version-X>
    And node B has <Cordapp-Version-Y> finance app installed
    When the network is ready
    Then node A can transfer 100 tokens to node B

    Examples:
      | Corda-Node-Version-X | Cordapp-Version-X     | Cordapp-Version-Y    |
      | corda-master         | finance-V1-flowImpl1  | finance-V2-flowImpl2 |
      | r3-master            | finance-V1-flowImpl1  | finance-V2-flowImpl2 |

  Scenario Outline: Corda node can transact with another Corda node where each has a Cordapp version with different, but backwards compatible, Flow versions
    Given a node A of version <Corda-Node-Version-X>
    And node A has <Cordapp-Version-X> finance app installed
    And a node B of version <Corda-Node-Version-X>
    And node B has <Cordapp-Version-Y> finance app installed
    When the network is ready
    Then node A can transfer 100 tokens to node B

    Examples:
      | Corda-Node-Version-X | Cordapp-Version-X  | Cordapp-Version-Y |
      | corda-master         | finance-V1-flowV1  | finance-V2-flowV2 |
      | r3-master            | finance-V1-flowV1  | finance-V2-flowV2 |

  Scenario Outline: Corda node fails to transact with another Corda node where each has a Cordapp version with different, incompatible, Flow versions
    Given a node A of version <Corda-Node-Version-X>
    And node A has <Cordapp-Version-X> finance app installed
    And a node B of version <Corda-Node-Version-X>
    And node B has <Cordapp-Version-Y> finance app installed
    When the network is ready
    Then node A fails to transfer 100 tokens to node B

    Examples:
      | Corda-Node-Version-X | Cordapp-Version-X           | Cordapp-Version-Y          |
      | corda-master         | finance-V1-flowV1-incompat  | finance-V2-flowV2-incompat |
      | r3-master            | finance-V1-flowV1-incompat  | finance-V2-flowV2-incompat |
