@compatibility @node @configuration
Feature: Compatibility - Node configuration
  To support an interoperable Corda network, a Corda node must have the ability to transact with another Corda node
  when configuration changes are applied independently to each node.
  Configuration changes may be classified into three types:
  1. Global configuration items that affect all nodes (eg. change of `compatibilityZoneURL` in an R3 network)
  2. Corda Enterprise specific configuration items (eg. `relay` configuration, `security` using Apache Shiro)
  3. General configuration items applicable to both OS and Corda Enterprise distributions (`database`, identity, addresses for p2p/rpc/web/ssh, jmx configuration, etc)
  TODO: implementation to provide two modes of operation (spin-up before/after change, spin-up with change-only)

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
      | corda-3.0            |
      | corda-3.1            |
      | corda-master         |

  Scenario Outline: Corda Enterprise node can transact with another Corda Enterprise node where node configuration is changed on one of the Corda Enterprise nodes
    Given a node A of version <Corda-Node-Version-X>
    And node A has the finance app installed
    And node A configuration is changed
    And a node B of version <Corda-Node-Version-X>
    And node B has the finance app installed
    When the network is ready
    Then node A can transfer 100 tokens to node B

    Examples:
      | Corda-Node-Version-X         |
      | R3.CORDA-3.0.0-DEV-PREVIEW-3 |
      | r3-master                    |

  Scenario Outline: Corda Enterprise node can transact with another Corda (OS) node where node configuration is changed on both OS and Corda Enterprise nodes
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
      | R3.CORDA-3.0.0-DEV-PREVIEW-3 | corda-3.0    |
      | R3.CORDA-3.0.0-DEV-PREVIEW-3 | corda-3.1    |
      | r3-master                    | corda-master |

  Scenario Outline: Add scenarios where new configuration items are added.
    Examples: TODO