@compatibility @node
Feature: Compatibility - Node versions
  To support an interoperable Corda network, a Corda (OS) node must have the ability to transact with an R3 Corda (Enterprise) node for the same version.

  Scenario Outline: Startup a Corda (OS) Node
    Given a node A of version <Corda-Node-Version>
    When the network is ready

    Examples:
      | Corda-Node-Version    |
      | corda-3.0             |

  Scenario Outline: Startup a Corda (OS) Node from Artifactory
    Given a node A of version <Corda-Node-Version>
    When the network is ready

    Examples:
      | Corda-Node-Version    |
      | corda-3.0-HC02        |

  Scenario Outline: Startup an R3 Corda Node from Artifactory
    Given a node A of version <Corda-Node-Version>
    When the network is ready

    Examples:
      | Corda-Node-Version    |
      | r3corda-3.0-DP2       |

  Scenario Outline: Corda (OS) Node can transact with R3 Corda (Enterprise) node, both using a default H2 database
    Given a node A of version <Corda-Node-Version>
    And node A uses database of type <Database-Type>
    And node A has the finance app installed
    And a node B of version <R3-Corda-Node-Version>
    And node B uses database of type <Database-Type>
    And node B has the finance app installed
    When the network is ready
#    Then node A has 1 issuable currency
    Then node B has 1 issuable currency
#    Then node A can transfer 100 tokens to node B

  Examples:
#  | Node-Version    | Database-Type     |
#  | MASTER          | H2                |
      | Corda-Node-Version    | R3-Corda-Node-Version   | Database-Type     |
      | corda-3.0             | r3corda-3.0             | H2                |
