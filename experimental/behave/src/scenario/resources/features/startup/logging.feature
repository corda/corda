@logging @startup
Feature: Startup Information - Logging
  A Corda node should inform the user of important parameters during startup so that he/she can confirm the setup and
  configure / connect relevant software to said node.

  Scenario: Node shows logging information on startup
    Given a node A of version MASTER
    And node A uses database of type H2
    And node A is located in London, GB
    When the network is ready
    Then user can retrieve logging information for node A

  Scenario: Node shows database details on startup
    Given a node A of version MASTER
    When the network is ready
    Then user can retrieve database details for node A

  Scenario: Node shows version information on startup
    Given a node A of version MASTER
    Then node A is on platform version 2
    And node A is on release version 3.0-SNAPSHOT
