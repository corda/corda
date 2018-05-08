@database @connectivity
Feature: Database - Connection
  For Corda to work, a database must be running and appropriately configured.

  Scenario Outline: User can connect to node's database
    Given a node PartyA of version <Node-Version>
    And node PartyA uses database of type <Database-Type>
    When the network is ready
    Then user can connect to the database of node PartyA

    Examples:
      | Node-Version    | Database-Type     |
      | r3-master          | H2                |

# To run this scenario using other DB providers you must ensure that Docker is running locally
#      | r3-master          | postgreSQL        |
#      | r3-master          | SQL Server        |