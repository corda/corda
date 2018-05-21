@logging @startup
Feature: Startup Information - Logging
  A Corda node should inform the user of important parameters during startup so that he/she can confirm the setup and
  configure / connect relevant software to said node.

  Scenario Outline: Node shows logging information on startup
    Given a node PartyA of version <Node-Version>
    And node PartyA uses database of type <Database-Type>
    And node PartyA is located in London, GB
    When the network is ready
    Then user can retrieve logging information for node PartyA

    Examples:
      | Node-Version   | Database-Type  |
      | master         | H2             |

  Scenario Outline: Node shows database details on startup
    Given a node PartyA of version <Node-Version>
    And node PartyA uses database of type <Database-Type>
    When the network is ready
    Then user can retrieve database details for node PartyA

    Examples:
      | Node-Version    | Database-Type  |
      | master          | H2             |

  Scenario Outline: Node shows version information on startup
    Given a node PartyA of version <Node-Version>
    Then node PartyA is on platform version <Platform-Version>
    And node PartyA is on release version <Release-Version>

    Examples:
      | Node-Version    | Platform-Version | Release-Version    |
      | master          | 4                | corda-4.0-snapshot |
