@compatibility @node @database
Feature: Compatibility - Database providers
  To support an interoperable Corda network, a Corda (OS) node must have the ability to transact with an R3 Corda (Enterprise) node using different database providers.

  Scenario Outline: QA: Corda (OS) Node using H2 can transact with R3 Corda (Enterprise) node using SQL Server, in an R3 Corda configured network.
    Given a node PartyA of version <R3-Corda-Node-Version> with proxy
    And node PartyA uses database of type <Database-Type>
    And node PartyA has the finance app installed
    And a node PartyB of version <Corda-Node-Version>
    And node PartyB has the finance app installed
    And a nonvalidating notary Notary of version <R3-Corda-Node-Version>
    When the network is ready
    Then node PartyA can issue 1000 <Currency>
    And node PartyA can transfer 100 <Currency> to node PartyB

    Examples:
      | Corda-Node-Version | R3-Corda-Node-Version   | Currency | Database-Type     |
      | corda-master       | r3-master               | GBP      | SQL Server        |

  Scenario Outline: QA: Corda (OS) Node using H2 can transact with R3 Corda (Enterprise) node using Postgres, in an R3 Corda configured network.
    Given a node PartyA of version <R3-Corda-Node-Version> with proxy
    And node PartyA uses database of type <Database-Type>
    And node PartyA has the finance app installed
    And a node PartyB of version <Corda-Node-Version>
    And node PartyB has the finance app installed
    And a nonvalidating notary Notary of version <R3-Corda-Node-Version>
    When the network is ready
    Then node PartyA can issue 1000 <Currency>
    And node PartyA can transfer 100 <Currency> to node PartyB

    Examples:
      | Corda-Node-Version | R3-Corda-Node-Version   | Currency | Database-Type   |
      | corda-master       | r3-master               | GBP      | postgres        |

  Scenario Outline: QA: 3 Nodes can transact with each other using different database providers: H2, SQL Server, PostgreSQL
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
      | r3-master               | GBP      | h2              | sql-server      | postgres        |

  Scenario Outline: Add Doorman and NMS database usage scenarios
    Examples: TODO