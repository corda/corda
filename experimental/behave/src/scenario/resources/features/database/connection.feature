@database @connectivity
Feature: Database - Connection
  For Corda to work, a database must be running and appropriately configured.

  Scenario Outline: User can connect to node's database
    Given a node A of version <Node-Version>
    And node A uses database of type <Database-Type>
    When the network is ready
    Then user can connect to the database of node A

    Examples:
      | Node-Version    | Database-Type     |
      | MASTER          | H2                |
     #| MASTER          | SQL Server        |