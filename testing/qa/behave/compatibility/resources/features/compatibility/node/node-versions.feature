@compatibility @node
Feature: Compatibility - Node versions
  To support an interoperable Corda network, a Corda (OS) node must have the ability to transact with an R3 Corda (Enterprise) node for the same version.

  Scenario Outline: Startup a Corda (OS) Node
    Given a node PartyA of version <Corda-Node-Version>
    When the network is ready

    Examples:
      | Corda-Node-Version           |
      | corda-3.0                    |
      | corda-3.1                    |
      | R3.CORDA-3.0.0-DEV-PREVIEW-3 |

  Scenario Outline: Startup a Corda (OS) Node and print its node information
    Given a node PartyA of version <Corda-Node-Version>
    When the network is ready
    Then user can retrieve logging information for node A
    And user can retrieve node identity information for node A

    Examples:
      | Corda-Node-Version           |
      | corda-3.0                    |
      | corda-3.1                    |
      | R3.CORDA-3.0.0-DEV-PREVIEW-3 |

  Scenario Outline: Startup a Corda (OS) Node with several Cordapps deployed.
    Given a node PartyA of version <Corda-Node-Version>
    And node PartyA has app installed: net.corda:bank-of-corda-demo-corda:CORDA_VERSION
    And node PartyA has app installed: net.corda:trader-demo-corda:CORDA_VERSION
    When the network is ready
    Then user can retrieve logging information for node A
    And user can retrieve node identity information for node A

    Examples:
      | Corda-Node-Version           |
      | corda-3.0                    |
      | corda-3.1                    |
      | R3.CORDA-3.0.0-DEV-PREVIEW-3 |
