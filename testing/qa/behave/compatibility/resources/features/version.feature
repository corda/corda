@logging @startup
Feature: Startup Information - Logging
  A Corda node should inform the user of important parameters during startup so that he/she can confirm the setup and
  configure / connect relevant software to said node.

  Scenario Outline: Node shows version information on startup
    Given a node PartyA of version <Node-Version>
    When the network is ready
    Then node PartyA is on platform version <Platform-Version>
    And node PartyA is on release version <Release-Version>

    Examples:
      | Node-Version     | Platform-Version | Release-Version |
      | ENT-4.0-SNAPSHOT | 4                | 4.0-SNAPSHOT    |
      | ENT-3.0          | 3                | 3.0             |
      | OS-4.0-SNAPSHOT  | 4                | 4.0-SNAPSHOT    |
      | 3.1-corda        | 3                | 3.1-corda       |
      | corda-3.0        | 3                | corda-3.0       |
