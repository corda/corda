@compatibility @node
Feature: Compatibility - Node versions
  To support an interoperable Corda network, a Corda (OS) node must have the ability to transact with an R3 Corda (Enterprise) node for the same version.

  Scenario Outline: Startup a Corda (OS) Node
    Given a node PartyA of version <Corda-Node-Version>
    When the network is ready

    Examples:
      | Corda-Node-Version    |
      | corda-3.0             |

  Scenario Outline: Startup a Corda (OS) Node from Artifactory
    Given a node PartyA of version <Corda-Node-Version>
    And a node PartyB of version <Corda-Node-Version>
    When the network is ready

    Examples:
      | Corda-Node-Version    |
      | corda-3.0-RC01        |

  Scenario Outline: Startup an R3 Corda Node from Artifactory
    Given a node PartyA of version <Corda-Node-Version>
    When the network is ready

    Examples:
      | Corda-Node-Version    |
      | r3corda-3.0-DP2       |

  Scenario Outline: Startup a Corda (OS) Node and print its node information
    Given a node PartyA of version <Corda-Node-Version>
    When the network is ready
    Then user can retrieve logging information for node A
    And user can retrieve node identity information for node A

    Examples:
      | Corda-Node-Version    |
      | corda-3.0-HC02        |

  Scenario Outline: Startup a Corda (OS) Node with several Cordapps deployed.
    Given a node PartyA of version <Corda-Node-Version>
#    And node PartyA has app installed: net.corda:bank-of-corda-demo-corda:CORDA_VERSION
#    And node PartyA has app installed: net.corda:trader-demo-corda:CORDA_VERSION
    When the network is ready
    Then user can retrieve logging information for node A
    And user can retrieve node identity information for node A

    Examples:
      | Corda-Node-Version    |
      | corda-3.0-HC02        |

  Scenario Outline: Startup a Corda (OS) Node and issue some currency
    Given a node PartyA of version <Corda-Node-Version>
    And a nonvalidating notary Notary of version <Corda-Node-Version>
    When the network is ready
    Then node PartyA can issue 1000 <Currency>

    Examples:
      | Corda-Node-Version    | Currency |
      | corda-3.0-HC02        | GBP      |

  Scenario Outline: R3 Corda (Enterprise) Node can transact with Corda (OS) node, in a Corda OS configured network
    Given a node PartyA of version <Corda-Node-Version>
    And node PartyA has the finance app installed
    And a node PartyB of version <R3-Corda-Node-Version>
    And node PartyB has the finance app installed
    And a nonvalidating notary Notary of version <Corda-Node-Version>
    When the network is ready
    Then node PartyA can issue 1000 <Currency>
    And node PartyA can transfer 100 <Currency> to node B

    Examples:
      | Corda-Node-Version    | R3-Corda-Node-Version | Currency |
      | corda-3.0-RC01        | r3-master             | GBP      |

  Scenario Outline: Corda (OS) Node can transact with R3 Corda (Enterprise) node, in an R3 Corda configured network.
    Given a node PartyA of version <R3-Corda-Node-Version>
    And node PartyA has the finance app installed
    And a node PartyB of version <Corda-Node-Version>
    And node PartyB has the finance app installed
    And a nonvalidating notary Notary of version <R3-Corda-Node-Version>
    When the network is ready
    Then node PartyA can issue 1000 <Currency>
    And node PartyA can transfer 100 <Currency> to node B

    Examples:
      | Corda-Node-Version | R3-Corda-Node-Version   | Currency |
      | corda-3.0-RC02     | r3-master               | GBP      |