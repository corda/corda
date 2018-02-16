@logging @startup
Feature: Startup Information - Logging
  A Corda node should inform the user of important parameters during startup so that he/she can confirm the setup and
  configure / connect relevant software to said node.

  Scenario: Node shows logging information on startup
    Given a node PartyA of version corda-3.0
    And node PartyA uses database of type H2
    And node PartyA is located in London, GB
    When the network is ready
    Then user can retrieve logging information for node PartyA

  Scenario: Node shows database details on startup
    Given a node PartyA of version master
    When the network is ready
    Then user can retrieve database details for node PartyA

  Scenario: Node shows version information on startup
    Given a node PartyA of version master
    Then node PartyA is on platform version 2
    And node PartyA is on release version master
