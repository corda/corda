@compatibility @cordapps @upgrade @migration
Feature: Compatibility - CorDapp upgrades and migrations
  To support an interoperable Corda network, a CordaApp must be upgradeable across different Corda distributions and
  node versions (and without loss of transactions between participating nodes in a flow)

  Scenario Outline: CorDapp running on Corda OS node using default database (H2) can be migrated to an R3 Corda (Enterprise) node without loss of data.
    Given a node A of version <Corda-Node-Version-X>
    And node A has <Cordapp-Version-X> finance app installed
    And a node B of version <Corda-Node-Version-X>
    And node B has <Cordapp-Version-Y> finance app installed
    When the network is ready
    Then node A can issue 1000 <Currency>
    And node A can transfer 100 <Currency> to node B
    And node A is migrated to <Corda-Node-Version-Y>
    And node B can transfer 100 <Currency> to node A

  Examples:
    | Corda-Node-Version-X | Corda-Node-Version-Y | Cordapp-Version-X | Cordapp-Version-Y |
    | corda-3.0-RC01       | r3corda-3.0-DP2      | finance-V1        | finance-V2        |

  Scenario Outline: as above but Node B has not processed Node A payment flow transactions before upgrade
    Given a node A of version <Corda-Node-Version-X>
    And node A has <Cordapp-Version-X> finance app installed
    When the network is ready
    Then node A can issue 1000 <Currency>
    And node A can transfer 100 <Currency> to node B
    And node B has version <Corda-Node-Version-X>
    And node B has <Cordapp-Version-Y> finance app installed
    And node A is migrated to <Corda-Node-Version-Y>
    And node B can transfer 100 <Currency> to node A

    Examples:
      | Corda-Node-Version-X | Corda-Node-Version-Y | Cordapp-Version-X | Cordapp-Version-Y |
      | corda-3.0-RC01       | r3corda-3.0-DP2      | finance-V1        | finance-V2        |

  Scenario Outline: as above but Node B has not processed Node A payment flow transactions before upgrade so we enforce flow draining mode before upgrade can complete
    Given a node A of version <Corda-Node-Version-X>
    And node A has <Cordapp-Version-X> finance app installed
    When the network is ready
    Then node A can issue 1000 <Currency>
    And node A can transfer 100 <Currency> to node B
    And node A enables flow draining mode
    And node B has version <Corda-Node-Version-X>
    And node B has <Cordapp-Version-Y> finance app installed
    And node A is migrated to <Corda-Node-Version-Y>
    And node B can transfer 100 <Currency> to node A

    Examples:
      | Corda-Node-Version-X | Corda-Node-Version-Y | Cordapp-Version-X | Cordapp-Version-Y |
      | corda-3.0-RC01       | r3corda-3.0-DP2      | finance-V1        | finance-V2        |
