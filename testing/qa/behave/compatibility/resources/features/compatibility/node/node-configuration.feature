@compatibility @node @configuration
Feature: Compatibility - Node configuration
  To support an interoperable Corda network, a Corda node must have the ability to transact with another Corda node
  when configuration changes are applied independently to each node:

  Scenario Outline: Corda (OS) node can transact with another Corda (OS) node where node configuration is changed on one of the Corda OS nodes
    Given a node A of version <Corda-Node-Version-X>
    And node A configuration is changed
    And node A has the finance app installed
    And a node B of version <Corda-Node-Version-X>
    And node B has the finance app installed
    When the network is ready
    Then node A can transfer 100 tokens to node B

  Examples:
      | Corda-Node-Version-X |
      | corda-3.0       |

  Scenario Outline: R3 Corda node can transact with another R3 Corda node where node configuration is changed on one of the R3 Corda nodes
    Given a node A of version <Corda-Node-Version-X>
    And node A has the finance app installed
    And node A configuration is changed
    And a node B of version <Corda-Node-Version-X>
    And node B has the finance app installed
    When the network is ready
    Then node A can transfer 100 tokens to node B

    Examples:
      | Corda-Node-Version-X |
      | r3corda-3.0-DP3-RC01      |

  Scenario Outline: R3 Corda node can transact with another Corda (OS) node where where node configuration is changed on both of the R3 Corda nodes
    Given a node A of version <Corda-Node-Version-X>
    And node A configuration is changed
    And node A has the finance app installed
    And a node B of version <Corda-Node-Version-Y>
    And node B configuration is changed
    And node B has the finance app installed
    When the network is ready
    Then node A can transfer 100 tokens to node B

    Examples:
      | Corda-Node-Version-X | Corda-Node-Version-Y |
      | r3corda-3.0-DP3-RC01      | corda-3.0            |
