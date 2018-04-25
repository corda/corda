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
    And node PartyA can transfer 100 <Currency> to node PartyB

    Examples:
      | R3-Corda-Node-Version        | Corda-Node-Version | Currency |
      | r3-master                    | corda-master       | GBP      |
      | corda-3.0                    | corda-3.1          | GBP      |
      | R3.CORDA-3.0.0-DEV-PREVIEW-3 | corda-3.0          | GBP      |
      | R3.CORDA-3.0.0-DEV-PREVIEW-3 | corda-3.1          | GBP      |

  Scenario Outline: Corda (OS) Node using H2 database can transact with R3 Corda node using a sql-server database
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
      | R3-Corda-Node-Version        | Corda-Node-Version | Currency | Database-Type |
      | r3-master                    | corda-master       | GBP      | sql-server    |
      | corda-3.0                    | corda-3.1          | GBP      | sql-server    |
      | R3.CORDA-3.0.0-DEV-PREVIEW-3 | corda-3.0          | GBP      | sql-server    |
      | R3.CORDA-3.0.0-DEV-PREVIEW-3 | corda-3.1          | GBP      | sql-server    |
      | r3-master                    | corda-master       | GBP      | postgres      |
      | corda-3.0                    | corda-3.1          | GBP      | postgres      |
      | R3.CORDA-3.0.0-DEV-PREVIEW-3 | corda-3.0          | GBP      | postgres      |
      | R3.CORDA-3.0.0-DEV-PREVIEW-3 | corda-3.1          | GBP      | postgres      |

  Scenario Outline: R3 Corda network with R3 Corda nodes using different database providers and versions transacting between each other
    Given a node PartyA of version <R3-Corda-Node-Version> with proxy
    And node PartyA uses database of type <Database-Type-1>
    And node PartyA has the finance app installed
    And a node PartyB of version <R3-Corda-Node-Version> with proxy
    And node PartyB uses database of type <Database-Type-2>
    And node PartyB has the finance app installed
    And a node PartyC of version <R3-Corda-Node-Version> with proxy
    And node PartyC uses database of type <Database-Type-3>
    And node PartyC has the finance app installed
    And a nonvalidating notary Notary of version <R3-Corda-Node-Version>
    When the network is ready within 4 minutes
    Then node PartyA can issue 1000 <Currency>
    And node PartyA can transfer 100 <Currency> to node PartyB
    And node PartyB can transfer 100 <Currency> to node PartyC
    And node PartyC can transfer 100 <Currency> to node PartyA

    Examples:
      | R3-Corda-Node-Version        | Currency | Database-Type-1 | Database-Type-2 | Database-Type-3 |
      | r3-master                    | GBP      | h2              | sql-server      | postgres        |
      | R3.CORDA-3.0.0-DEV-PREVIEW-3 | GBP      | h2              | sql-server      | postgres        |

  Scenario Outline: Mixed OS and R3 Corda network using different database providers and versions transacting between each other
    Given a node PartyA of version <R3-Corda-Node-Version> with proxy
    And node PartyA uses database of type <Database-Type-1>
    And node PartyA has the finance app installed
    And a node PartyB of version <R3-Corda-Node-Version> with proxy
    And node PartyB uses database of type <Database-Type-2>
    And node PartyB has the finance app installed
    And a node PartyC of version <Corda-Node-Version> with proxy
    And node PartyC uses database of type <Database-Type-3>
    And node PartyC has the finance app installed
    And a nonvalidating notary Notary of version <R3-Corda-Node-Version>
    When the network is ready within 4 minutes
    Then node PartyA can issue 1000 <Currency>
    And node PartyA can transfer 100 <Currency> to node PartyB
    And node PartyB can transfer 100 <Currency> to node PartyC
    And node PartyC can transfer 100 <Currency> to node PartyA

    Examples:
      | R3-Corda-Node-Version        | Corda-Node-Version | Currency | Database-Type-1 | Database-Type-2 | Database-Type-3 |
      | r3-master                    | corda-master       | GBP      | sql-server      | postgres        | h2              |
      | R3.CORDA-3.0.0-DEV-PREVIEW-3 | corda-3.0          | GBP      | sql-server      | postgres        | h2              |
      | R3.CORDA-3.0.0-DEV-PREVIEW-3 | corda-3.1          | GBP      | sql-server      | postgres        | h2              |
