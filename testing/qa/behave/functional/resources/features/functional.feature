@qa
Feature: QA Operational
  An Corda Enterprise network must support cash transactions using multiple database providers.

  Scenario Outline: QA: Stand up a basic Corda Enterprise Network with one node that can verify its identity
    Given a node PartyA of version <R3-Corda-Node-Version>
    And node PartyA has the finance app installed
    When the network is ready
    Then node PartyA is on platform version 4
    And node PartyA is on release version <Version-label>
    And user can retrieve node identity information for node PartyA

    Examples:
      | R3-Corda-Node-Version   | Version-label     |
      | r3-master               |  3.0.0-SNAPSHOT   |

  Scenario Outline: QA: Stand up a basic Corda Enterprise Network with one node and a notary; node can issue cash to itself
    Given a node PartyA of version <R3-Corda-Node-Version>
    And a nonvalidating notary Notary of version <R3-Corda-Node-Version>
    And node PartyA has the finance app installed
    When the network is ready
    Then node PartyA can issue 1000 <Currency>

    Examples:
      | R3-Corda-Node-Version    | Currency |
      | r3-master                | GBP      |

  Scenario Outline: User can connect to a Corda Enterprise node using a SQL Server database
    Given a node PartyA of version <Node-Version>
    And node PartyA uses database of type <Database-Type>
    When the network is ready
    Then user can connect to the database of node PartyA

    Examples:
      | Node-Version             | Database-Type     |
      | r3-master                | SQL Server        |

  Scenario Outline: QA: Node using H2 can transact with node using SQL Server, in a Corda Enterprise configured network.
    Given a node PartyA of version <R3-Corda-Node-Version>
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

  Scenario Outline: User can connect to a Corda Enterprise node using a PostgreSQL database
    Given a node PartyA of version <Node-Version>
    And node PartyA uses database of type <Database-Type>
    When the network is ready
    Then user can connect to the database of node PartyA

    Examples:
      | Node-Version            | Database-Type   |
      | r3-master               | postgres        |

  Scenario Outline: QA: Node using H2 can transact with node using Postgres, in a Corda Enterprise configured network.
    Given a node PartyA of version <R3-Corda-Node-Version>
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

  Scenario Outline: User can connect to a Corda Enterprise node using an Oracle 11g database
    Given a node PartyA of version <Node-Version>
    And node PartyA uses database of type <Database-Type>
    When the network is ready
    Then user can connect to the database of node PartyA

    Examples:
      | Node-Version            | Database-Type   |
      | r3-master               | oracle11g       |

  Scenario Outline: QA: Node using H2 can transact with node using Oracle 11g, in a Corda Enterprise configured network.
    Given a node PartyA of version <R3-Corda-Node-Version>
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
      | r3-master               | GBP      | oracle11g       |

  Scenario Outline: User can connect to a Corda Enterprise node using an Oracle 12c database
    Given a node PartyA of version <Node-Version>
    And node PartyA uses database of type <Database-Type>
    When the network is ready
    Then user can connect to the database of node PartyA

    Examples:
      | Node-Version            | Database-Type   |
      | r3-master               | oracle12c       |

  Scenario Outline: QA: Node using H2 can transact with node using Oracle 12c, in a Corda Enterprise configured network.
    Given a node PartyA of version <R3-Corda-Node-Version>
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
      | r3-master               | GBP      | oracle12c       |