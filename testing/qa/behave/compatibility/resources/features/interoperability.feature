@qa
Feature: QA Interoperability
  To support an interoperable Corda network, different CorDapps must have the ability to transact in mixed Corda (OS) and Corda Enterprise networks.

  Scenario Outline: Corda (OS) Node can transact with Corda Enterprise node, in a Corda Enterprise configured network.
    Given a node PartyA of version <Party-A-Version>
    And node PartyA has the finance app installed
    And a node PartyB of version <Party-B-Version>
    And node PartyB has the finance app installed
    And a nonvalidating notary Notary of version <Party-A-Version>
    When the network is ready
    Then node PartyA can issue 1000 <Currency>
    And node PartyA can transfer 100 <Currency> to node PartyB

    Examples:
      | Party-A-Version | Party-B-Version              | Currency |
      | ENT-3.0         | ENT-4.0-SNAPSHOT             | GBP      |
      | ENT-3.0         | OS-4.0-SNAPSHOT              | GBP      |
      | ENT-3.0         | 3.1-corda                    | GBP      |
      | ENT-3.0         | corda-3.0                    | GBP      |

  Scenario Outline: Corda (OS) Node using H2 database can transact with Corda Enterprise node using a commercial database
    Given a node PartyA of version <R3-Corda-Node-Version>
    And node PartyA uses database of type <Database-Type>
    And node PartyA has the finance app installed
    And a node PartyB of version <Corda-Node-Version>
    And node PartyB has the finance app installed
    And a nonvalidating notary Notary of version <R3-Corda-Node-Version>
    When the network is ready
    Then node PartyA can issue 1000 <Currency>
    And node PartyA can transfer 100 <Currency> to node PartyB

    Examples:
      | R3-Corda-Node-Version | Corda-Node-Version           | Currency | Database-Type |
      | ENT-3.0               | ENT-4.0-SNAPSHOT             | GBP      | sql-server    |
      | ENT-3.0               | OS-4.0-SNAPSHOT              | GBP      | sql-server    |
      | ENT-3.0               | 3.1-corda                    | GBP      | sql-server    |
      | ENT-3.0               | corda-3.0                    | GBP      | sql-server    |
      | ENT-3.0               | ENT-4.0-SNAPSHOT             | GBP      | postgres      |
      | ENT-3.0               | OS-4.0-SNAPSHOT              | GBP      | postgres      |
      | ENT-3.0               | 3.1-corda                    | GBP      | postgres      |
      | ENT-3.0               | corda-3.0                    | GBP      | postgres      |
      | ENT-3.0               | ENT-4.0-SNAPSHOT             | GBP      | oracle11g     |
      | ENT-3.0               | OS-4.0-SNAPSHOT              | GBP      | oracle11g     |
      | ENT-3.0               | 3.1-corda                    | GBP      | oracle11g     |
      | ENT-3.0               | corda-3.0                    | GBP      | oracle11g     |
      | ENT-3.0               | ENT-4.0-SNAPSHOT             | GBP      | oracle12c     |
      | ENT-3.0               | OS-4.0-SNAPSHOT              | GBP      | oracle12c     |
      | ENT-3.0               | 3.1-corda                    | GBP      | oracle12c     |
      | ENT-3.0               | corda-3.0                    | GBP      | oracle12c     |

  Scenario Outline: Corda Enterprise network with Corda Enterprise nodes using different database providers and versions transacting between each other
    Given a node PartyA of version <R3-Corda-Node-Version>
    And node PartyA uses database of type <Database-Type-1>
    And node PartyA has the finance app installed
    And a node PartyB of version <R3-Corda-Node-Version>
    And node PartyB uses database of type <Database-Type-2>
    And node PartyB has the finance app installed
    And a node PartyC of version <R3-Corda-Node-Version>
    And node PartyC uses database of type <Database-Type-3>
    And node PartyC has the finance app installed
    And a nonvalidating notary Notary of version <R3-Corda-Node-Version>
    When the network is ready within 4 minutes
    Then node PartyA can issue 1000 <Currency>
    And node PartyA can transfer 100 <Currency> to node PartyB
    And node PartyB can transfer 100 <Currency> to node PartyC
    And node PartyC can transfer 100 <Currency> to node PartyA

    Examples:
      | R3-Corda-Node-Version | Currency | Database-Type-1 | Database-Type-2 | Database-Type-3 |
      | ENT-3.0               | GBP      | h2              | sql-server      | postgres        |

  Scenario Outline: Mixed OS and Corda Enterprise network using different database providers and versions transacting between each other
    Given a node PartyA of version <R3-Corda-Node-Version>
    And node PartyA uses database of type <Database-Type-1>
    And node PartyA has the finance app installed
    And a node PartyB of version <R3-Corda-Node-Version>
    And node PartyB uses database of type <Database-Type-2>
    And node PartyB has the finance app installed
    And a node PartyC of version <Corda-Node-Version>
    And node PartyC uses database of type <Database-Type-3>
    And node PartyC has the finance app installed
    And a nonvalidating notary Notary of version <R3-Corda-Node-Version>
    When the network is ready within 4 minutes
    Then node PartyA can issue 1000 <Currency>
    And node PartyA can transfer 100 <Currency> to node PartyB
    And node PartyB can transfer 100 <Currency> to node PartyC
    And node PartyC can transfer 100 <Currency> to node PartyA

    Examples:
      | R3-Corda-Node-Version | Corda-Node-Version           | Currency | Database-Type-1 | Database-Type-2 | Database-Type-3 |
      | ENT-3.0               | ENT-4.0-SNAPSHOT             | GBP      | sql-server      | postgres        | h2              |
      | ENT-3.0               | OS-4.0-SNAPSHOT              | GBP      | sql-server      | postgres        | h2              |
      | ENT-3.0               | 3.1-corda                    | GBP      | sql-server      | postgres        | h2              |
      | ENT-3.0               | corda-3.0                    | GBP      | sql-server      | postgres        | h2              |
