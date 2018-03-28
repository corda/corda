@qa
Feature: QA Interoperability
  To support an interoperable Corda network, different CorDapps must have the ability to transact in mixed Corda (OS) and R3 Corda (Enterprise) networks.

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
      | R3-Corda-Node-Version   | Corda-Node-Version | Currency |
#      | r3-master               | corda-master     | GBP      |
#      | r3-master               | corda-3.0        | GBP      |
      | r3corda-3.0-DP3-RC01    | corda-3.0          | GBP      |

  Scenario Outline: Corda (OS) Node using H2 database can transact with R3 Corda node using a sql-server database
    Given a node PartyA of version <R3-Corda-Node-Version> with proxy
    And node PartyA uses database of type <Database-Type>
    And node PartyA has the finance app installed
    And a node PartyB of version <Corda-Node-Version>
    And node PartyB has the finance app installed
    And a nonvalidating notary Notary of version <R3-Corda-Node-Version>
    When the network is ready
    Then node PartyA can issue 1000 <Currency>
    And node PartyA can transfer 100 <Currency> to node B

    Examples:
      | R3-Corda-Node-Version   | Corda-Node-Version | Currency | Database-Type     |
#      | r3-master               | corda-3.0        | GBP      | sql-server        |
      | r3corda-3.0-DP3-RC01    | corda-3.0          | GBP      | sql-server        |

  Scenario Outline: Corda (OS) Node using H2 database can transact with R3 Corda node using a postgres database
    Given a node PartyA of version <R3-Corda-Node-Version> with proxy
    And node PartyA uses database of type <Database-Type>
    And node PartyA has the finance app installed
    And a node PartyB of version <Corda-Node-Version>
    And node PartyB has the finance app installed
    And a nonvalidating notary Notary of version <R3-Corda-Node-Version>
    When the network is ready
    Then node PartyA can issue 1000 <Currency>
    And node PartyA can transfer 100 <Currency> to node B

    Examples:
      | R3-Corda-Node-Version   | Corda-Node-Version | Currency | Database-Type     |
#      | r3-master               | corda-3.0        | GBP      | postgres        |
      | r3corda-3.0-DP3-RC01    | corda-3.0          | GBP      | postgres        |

  Scenario Outline: Corda network of an (OS) Node using H2 database and R3 Corda nodes using different commercial database providers and versions transacting between each other
    Given a node PartyA of version <R3-Corda-Node-Version> with proxy
    And node PartyA uses database of type <Database-Type-1>
    And node PartyA has the finance app installed
    And a node PartyB of version <R3-Corda-Node-Version>
    And node PartyB uses database of type <Database-Type-2>
    And node PartyB has the finance app installed
    And a node PartyC of version <R3-Corda-Node-Version>
    And node PartyC uses database of type <Database-Type-3>
    And node PartyC has the finance app installed
    And a nonvalidating notary Notary of version <R3-Corda-Node-Version>
    When the network is ready
    Then node PartyA can issue 1000 <Currency>
    And node PartyA can transfer 100 <Currency> to node B
    And node PartyB can transfer 100 <Currency> to node C
    And node PartyC can transfer 100 <Currency> to node A

    Examples:
      | R3-Corda-Node-Version   | Currency | Database-Type-1 | Database-Type-2 | Database-Type-3 |
#      | r3-master               | GBP      | h2              | sql-server      | postgres        |
      | r3corda-3.0-DP3-RC01    | GBP      | h2              | sql-server      | postgres        |