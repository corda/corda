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

  Scenario Outline: QA: Corda (OS) Node can transact with R3 Corda (Enterprise) node, in an R3 Corda configured network.
    Given a node PartyA of version <R3-Corda-Node-Version> with proxy
    And node PartyA has the finance app installed
    And a node PartyB of version <Corda-Node-Version>
    And node PartyB has the finance app installed
    And a nonvalidating notary Notary of version <R3-Corda-Node-Version>
    When the network is ready
    Then node PartyA can issue 1000 <Currency>
    And node PartyA can transfer 100 <Currency> to node B

    Examples:
      | Corda-Node-Version | R3-Corda-Node-Version   | Currency |
      | corda-3.0          | r3-master               | GBP      |
#      | corda-3.0          | r3corda-3.0-DP3         | GBP      |

  Scenario Outline: User can connect to an R3 Corda database
    Given a node PartyA of version <Node-Version>
    And node PartyA uses database of type <Database-Type>
    When the network is ready
    Then user can connect to the database of node PartyA

    Examples:
      | Node-Version    | Database-Type     |
      | r3-master       | SQL Server        |

  Scenario Outline: QA: Corda (OS) Node using H2 can transact with R3 Corda (Enterprise) node using SQL Server, in an R3 Corda configured network.
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
      | Corda-Node-Version | R3-Corda-Node-Version   | Currency | Database-Type     |
      | corda-3.0          | r3-master               | GBP      | SQL Server        |
#      | corda-3.0          | r3corda-3.0-DP3         | GBP      |

  Scenario Outline: User can connect to node's database
    Given a node PartyA of version <Node-Version>
    And node PartyA uses database of type <Database-Type>
    When the network is ready
    Then user can connect to the database of node PartyA

    Examples:
      | Node-Version    | Database-Type     |
      | corda-3.0       | SQL Server        |