@qa
Feature: QA Operational
  An R3 Corda network must support cash transactions using multiple database providers.

  Scenario Outline: QA: Stand up a basic R3 Corda Network with one node that can verify its identity
    Given a node PartyA of version <R3-Corda-Node-Version> with proxy
    And node PartyA has the finance app installed
    When the network is ready
    Then node PartyA is on platform version 4
    And node PartyA is on release version R3.CORDA-3.0.0-DEV-PREVIEW-3
    And user can retrieve node identity information for node PartyA

    Examples:
      | R3-Corda-Node-Version        |
      | R3.CORDA-3.0.0-DEV-PREVIEW-3 |

  Scenario Outline: QA: Stand up a basic R3 Corda Network with one node and a notary; node can issue cash to itself
    Given a node PartyA of version <R3-Corda-Node-Version> with proxy
    And a nonvalidating notary Notary of version <R3-Corda-Node-Version>
    And node PartyA has the finance app installed
    When the network is ready
    Then node PartyA can issue 1000 <Currency>

    Examples:
      | R3-Corda-Node-Version        | Currency |
      | R3.CORDA-3.0.0-DEV-PREVIEW-3 | GBP      |

  Scenario Outline: User can connect to an R3 Corda node using a SQL Server database
    Given a node PartyA of version <Node-Version>
    And node PartyA uses database of type <Database-Type>
    When the network is ready
    Then user can connect to the database of node PartyA

    Examples:
      | Node-Version                 | Database-Type     |
      | R3.CORDA-3.0.0-DEV-PREVIEW-3 | SQL Server        |

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
      | R3-Corda-Node-Version        | Currency | Database-Type     |
      | R3.CORDA-3.0.0-DEV-PREVIEW-3 | GBP      | SQL Server        |

  Scenario Outline: User can connect to an R3 Corda node using a PostgreSQL database
    Given a node PartyA of version <Node-Version>
    And node PartyA uses database of type <Database-Type>
    When the network is ready
    Then user can connect to the database of node PartyA

    Examples:
      | Node-Version                 | Database-Type   |
      | R3.CORDA-3.0.0-DEV-PREVIEW-3 | postgres        |

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
      | R3-Corda-Node-Version        | Currency | Database-Type   |
      | R3.CORDA-3.0.0-DEV-PREVIEW-3 | GBP      | postgres        |
