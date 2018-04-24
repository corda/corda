@compatibility @cordapps @flows
Feature: Compatibility - CorDapp versions
  To support an interoperable Corda network, a Corda node must have the ability to transact with another Corda node
  when each node has a different version of the same cordapp for different Contract change scenarios.

  Scenario Outline: Corda node can transact with another Corda node where each has a Cordapp version with different Contract verify but same State definition
    Given a node A of version <Corda-Node-Version-X>
    And node A has <Cordapp-Version-X> finance app installed
    And a node B of version <Corda-Node-Version-X>
    And node B has <Cordapp-Version-Y> finance app installed
    When the network is ready
    Then node A can transfer 100 tokens to node B

    Examples:
      | Corda-Node-Version-X | Cordapp-Version-X    | Cordapp-Version-Y   |
      | corda-3.0            | finance-V1-contract  | finance-V2-contract |

  Scenario Outline: Corda node can transact with another Corda node where each has a Cordapp version with same Contract verify but different State definition
    Given a node A of version <Corda-Node-Version-X>
    And node A has <Cordapp-Version-X> finance app installed
    And a node B of version <Corda-Node-Version-X>
    And node B has <Cordapp-Version-Y> finance app installed
    And node B has upgraded <Cordapp-Version-Y> contract
    When the network is ready
    Then node A can transfer 100 tokens to node B

    Examples:
      | Corda-Node-Version-X | Cordapp-Version-X    | Cordapp-Version-Y   |
      | corda-3.0            | finance-V1-state     | finance-V2-state    |

  Scenario Outline: Corda node can transact with another Corda node where each has a Cordapp version with same Contract verify but different State definition and custom schemas
    Given a node A of version <Corda-Node-Version-X>
    And node A has <Cordapp-Version-X> finance app installed
    And a node B of version <Corda-Node-Version-X>
    And node B has <Cordapp-Version-Y> finance app installed
    And node B has upgraded <Cordapp-Version-Y> contract
    When the network is ready
    Then node A can transfer 100 tokens to node B

    Examples:
      | Corda-Node-Version-X | Cordapp-Version-X        | Cordapp-Version-Y       |
      | corda-3.0            | finance-V1-state-schema  | finance-V2-state-schema |

  Scenario Outline: Unhappy path scenarios to be added.
    Examples: TODO