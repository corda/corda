@compatibility @node @database
Feature: Compatibility - Node databases
  To support an interoperable Corda network, a Corda (OS) node must have the ability to transact with an R3 Corda (Enterprise) node using different databases:
  H2, azure-sql, sql-server, postgress, oracle

  Scenario Outline: Corda (OS) Node using H2 database can transact with R3 Corda node also using an H2 database
    Given a node A of version <Corda-Node-Version>
    And node A uses database of type <Database-Type-A>
    And node A has the finance app installed
    And a node B of version <R3-Corda-Node-Version>
    And node B uses database of type <Database-Type-A>
    And node B has the finance app installed
    When the network is ready
    Then node A can transfer 100 tokens to node B
    Then node B can transfer 100 tokens to node A

    Examples:
      | Corda-Node-Version    | R3-Corda-Node-Version   | Database-Type-A  |
      | corda-3.0             | r3corda-3.0             | H2               |

  Scenario Outline: Corda (OS) Node using H2 database can transact with R3 Corda node using azure-sql database
    Given a node A of version <Corda-Node-Version>
    And node A uses database of type <Database-Type-A>
    And node A has the finance app installed
    And a node B of version <R3-Corda-Node-Version>
    And node B uses database of type <Database-Type-B>
    And node B has the finance app installed
    When the network is ready
    Then node A can transfer 100 tokens to node B
    Then node B can transfer 100 tokens to node A

  Examples:
      | Corda-Node-Version    | R3-Corda-Node-Version   | Database-Type-A  | Database-Type-B  |
      | corda-3.0             | r3corda-3.0             | H2               | azure-sql        |

  Scenario Outline: Corda (OS) Node using H2 database can transact with R3 Corda node using a sql-server database
    Given a node A of version <Corda-Node-Version>
    And node A uses database of type <Database-Type-A>
    And node A has the finance app installed
    And a node B of version <R3-Corda-Node-Version>
    And node B uses database of type <Database-Type-B>
    And node B has the finance app installed
    When the network is ready
    Then node A can transfer 100 tokens to node B
    Then node B can transfer 100 tokens to node A

    Examples:
      | Corda-Node-Version    | R3-Corda-Node-Version   | Database-Type-A  | Database-Type-B  |
      | corda-3.0             | r3corda-3.0             | H2               | sql-server       |

  Scenario Outline: Corda (OS) Node using H2 database can transact with R3 Corda node using an oracle 11g database
    Given a node A of version <Corda-Node-Version>
    And node A uses database of type <Database-Type-A>
    And node A has the finance app installed
    And a node B of version <R3-Corda-Node-Version>
    And node B uses database of type <Database-Type-B>
    And node B has the finance app installed
    When the network is ready
    Then node A can transfer 100 tokens to node B
    Then node B can transfer 100 tokens to node A

    Examples:
      | Corda-Node-Version    | R3-Corda-Node-Version   | Database-Type-A  | Database-Type-B  |
      | corda-3.0             | r3corda-3.0             | H2               | oracle11g        |

  Scenario Outline: Corda (OS) Node using H2 database can transact with R3 Corda node using an oracle 12c database
    Given a node A of version <Corda-Node-Version>
    And node A uses database of type <Database-Type-A>
    And node A has the finance app installed
    And a node B of version <R3-Corda-Node-Version>
    And node B uses database of type <Database-Type-B>
    And node B has the finance app installed
    When the network is ready
    Then node A can transfer 100 tokens to node B
    Then node B can transfer 100 tokens to node A

    Examples:
      | Corda-Node-Version    | R3-Corda-Node-Version   | Database-Type-A  | Database-Type-B  |
      | corda-3.0             | r3corda-3.0             | H2               | oracle12c        |

  Scenario Outline: Corda (OS) Node using H2 database can transact with R3 Corda node using a postgres database
    Given a node A of version <Corda-Node-Version>
    And node A uses database of type <Database-Type-A>
    And node A has the finance app installed
    And a node B of version <R3-Corda-Node-Version>
    And node B uses database of type <Database-Type-B>
    And node B has the finance app installed
    When the network is ready
    Then node A can transfer 100 tokens to node B
    Then node B can transfer 100 tokens to node A

    Examples:
      | Corda-Node-Version    | R3-Corda-Node-Version   | Database-Type-A  | Database-Type-B  |
      | corda-3.0             | r3corda-3.0             | H2               | postgres         |


  Scenario Outline: Corda network of an (OS) Node using H2 database and R3 Corda nodes using different commercial database providers and versions transacting between each other
    Given a node A of version <Corda-Node-Version>
    And node A uses database of type <Database-Type-A>
    And node A has the finance app installed
    And a node B of version <R3-Corda-Node-Version>
    And node B uses database of type <Database-Type-B>
    And node B has the finance app installed
    And a node C of version <R3-Corda-Node-Version>
    And node C uses database of type <Database-Type-C>
    And node C has the finance app installed
    And a node D of version <R3-Corda-Node-Version>
    And node D uses database of type <Database-Type-D>
    And node D has the finance app installed
    And a node E of version <R3-Corda-Node-Version>
    And node E uses database of type <Database-Type-E>
    And node E has the finance app installed
    And a node F of version <R3-Corda-Node-Version>
    And node F uses database of type <Database-Type-F>
    And node F has the finance app installed
    When the network is ready
    Then node A can transfer 100 tokens to node B
    Then node B can transfer 100 tokens to node C
    Then node C can transfer 100 tokens to node D
    Then node D can transfer 100 tokens to node E
    Then node E can transfer 100 tokens to node F
    Then node F can transfer 100 tokens to node A

    Examples:
      | Corda-Node-Version    | R3-Corda-Node-Version   | Database-Type-A  | Database-Type-B  | Database-Type-C  | Database-Type-D  | Database-Type-E  | Database-Type-F  |
      | corda-3.0             | r3corda-3.0             | H2               | azure-sql        | sql-server       | oracle11g        | oracle12c        | postgres         |