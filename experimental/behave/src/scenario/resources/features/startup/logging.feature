Feature: Startup Information - Logging
  A Corda node should inform the user of important parameters during startup so that he/she can confirm the setup and
  configure / connect relevant software to said node.

  Scenario: Node shows logging information on startup
    Given a node A of version MASTER
    And node A uses database of type H2
    And node A is located in London, GB
    When the network is ready
    Then node A is on platform version 2
    And node A is on version 3.0-SNAPSHOT
    And user can retrieve logging information for node A
    And user can retrieve database details for node A
