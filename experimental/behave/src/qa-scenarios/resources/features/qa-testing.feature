@compatibility @node
Feature: Compatibility - Node versions
  To support an interoperable Corda network, a Corda (OS) node must have the ability to transact with an R3 Corda (Enterprise) node for the same version.

  Scenario Outline: QA: Stand up a basic R3 Corda Network with one node that can verify its identity
    Given a node PartyA of version <R3-Corda-Node-Version> with proxy
    And node PartyA has the finance app installed
    When the network is ready
    Then node PartyA is on platform version 4
    And node PartyA is on release version R3.CORDA-3.0-SNAPSHOT
#    And user can retrieve node identity information for node PartyA

    Examples:
      | R3-Corda-Node-Version   |
      | r3-master               |

  Scenario Outline: QA: Stand up a basic R3 Corda Network with one node and a notary; node can issue cash to itself
    Given a node PartyA of version <R3-Corda-Node-Version> with proxy
    And a nonvalidating notary Notary of version <R3-Corda-Node-Version>
    And node PartyA has the finance app installed
    When the network is ready
    Then node PartyA can issue 1000 <Currency>

    Examples:
      | R3-Corda-Node-Version   | Currency |
      | r3-master               | GBP      |

  Scenario Outline: User can connect to an R3 Corda node using a SQL Server database
    Given a node PartyA of version <Node-Version>
    And node PartyA uses database of type <Database-Type>
    When the network is ready
    Then user can connect to the database of node PartyA

    Examples:
      | Node-Version    | Database-Type     |
      | r3-master       | SQL Server        |

  Scenario Outline: QA: Node using H2 can transact with node using SQL Server, in an R3 Corda configured network.
    Given a node PartyA of version <R3-Corda-Node-Version> with proxy
    And node PartyA uses database of type <Database-Type>
    And node PartyA has the finance app installed
    And a node PartyB of version <R3-Corda-Node-Version>
    And node PartyB has the finance app installed
    And a nonvalidating notary Notary of version <R3-Corda-Node-Version>
    When the network is ready
    Then node PartyA can issue 1000 <Currency>
    And node PartyA can transfer 100 <Currency> to node B

    Examples:
      | R3-Corda-Node-Version   | Currency | Database-Type     |
      | r3-master               | GBP      | SQL Server        |

  Scenario Outline: User can connect to an R3 Corda node using a PostgreSQL database
    Given a node PartyA of version <Node-Version>
    And node PartyA uses database of type <Database-Type>
    When the network is ready
    Then user can connect to the database of node PartyA

    Examples:
      | Node-Version    | Database-Type   |
      | r3-master       | postgres        |

  Scenario Outline: QA: Node using H2 can transact with node using Postgres, in an R3 Corda configured network.
    Given a node PartyA of version <R3-Corda-Node-Version> with proxy
    And node PartyA uses database of type <Database-Type>
    And node PartyA has the finance app installed
    And a node PartyB of version <R3-Corda-Node-Version>
    And node PartyB has the finance app installed
    And a nonvalidating notary Notary of version <R3-Corda-Node-Version>
    When the network is ready
    Then node PartyA can issue 1000 <Currency>
    And node PartyA can transfer 100 <Currency> to node B

    Examples:
      | R3-Corda-Node-Version   | Currency | Database-Type   |
      | r3-master               | GBP      | postgres        |

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