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
      | r3-master      | H2             |

  Scenario Outline: Node shows database details on startup
    Given a node PartyA of version <Node-Version>
    And node PartyA uses database of type <Database-Type>
    When the network is ready
    Then user can retrieve database details for node PartyA

    Examples:
      | Node-Version    | Database-Type  |
      | r3-master       | H2             |

  Scenario Outline: Node shows version information on startup
    Given a node PartyA of version <Node-Version>
    Then node PartyA is on platform version <Platform-Version>
    And node PartyA is on release version <Release-Version>

    Examples:
      | Node-Version    | Platform-Version | Release-Version       |
      | r3-master       | 4                | R3.CORDA-3.0-SNAPSHOT |

  Scenario Outline: Start-up a simple 3 node network with a non validating notary
    Given a node PartyA of version <Node1-Version>
    And a node PartyB of version <Node2-Version>
    And a node PartyC of version <Node3-Version>
    And a nonvalidating notary Notary of version <Notary-Version>
    When the network is ready
    Then user can retrieve logging information for node PartyA
    And user can retrieve logging information for node PartyB
    And user can retrieve logging information for node PartyC

    Examples:
      | Node1-Version | Node2-Version | Node3-Version | Notary-Version |
      | r3-master     | r3-master     | r3-master     | r3-master      |
