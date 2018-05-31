@qa compatibility @node @cordapps
Feature: Compatibility - Corda distributions (OS and Enterprise) running different CorDapps
  To support an interoperable Corda network, different CorDapps must have the ability to transact in mixed Corda (OS) and Corda Enterprise networks.

  Scenario Outline: Run the SIMM valuation demo in a Corda OS Network.
    Given a node PartyA of version <Corda-Node-Version> with proxy
    And node PartyA has app installed: <Cordapp-Name>
    And a node PartyB of version <Corda-Node-Version>
    And node PartyB has app installed: <Cordapp-Name>
    And a nonvalidating notary Notary of version <Corda-Node-Version>
    When the network is ready
    And node PartyA has loaded app <Cordapp-Name>
    And node PartyB has loaded app <Cordapp-Name>
    Then node PartyA can trade with node PartyB
    And node PartyA vault contains 1 states
    And node PartyB vault contains 1 states
    And node PartyA can run portfolio valuation
    And node PartyA portfolio valuation is <Valuation>
    And node PartyB portfolio valuation is <Valuation>

    Examples:
      | Corda-Node-Version | Cordapp-Name        | Valuation |
      | corda-3.0          | simm-valuation-demo | 12345     |

  Scenario Outline: Run the SIMM valuation demo in a Corda Enterprise Network.
    Given a node PartyA of version <R3-Corda-Node-Version> with proxy
    And node PartyA has app installed: <Cordapp-Name>
    And a node PartyB of version <R3-Corda-Node-Version>
    And node PartyB has app installed: <Cordapp-Name>
    And a nonvalidating notary Notary of version <Corda-Node-Version>
    When the network is ready
    And node PartyA has loaded app <Cordapp-Name>
    And node PartyB has loaded app <Cordapp-Name>
    Then node PartyA can trade with node PartyB
    And node PartyA vault contains 1 states
    And node PartyB vault contains 1 states
    And node PartyA can run portfolio valuation
    And node PartyA portfolio valuation is <Valuation>
    And node PartyB portfolio valuation is <Valuation>

    Examples:
      | R3-Corda-Node-Version        | Cordapp-Name        | Valuation |
      | R3.CORDA-3.0.0-DEV-PREVIEW-3 | simm-valuation-demo | 12345     |

  Scenario Outline: Corda (OS) Node can transact with Corda Enterprise node using the SIMM valuation demo.
    Given a node PartyA of version <Corda-Node-Version> with proxy
    And node PartyA has app installed: <Cordapp-Name>
    And a node PartyB of version <R3-Corda-Node-Version>
    And node PartyB has app installed: <Cordapp-Name>
    And a nonvalidating notary Notary of version <Corda-Node-Version>
    When the network is ready
    And node PartyA has loaded app <Cordapp-Name>
    And node PartyB has loaded app <Cordapp-Name>
    Then node PartyA can trade with node PartyB
    And node PartyA vault contains 1 states
    And node PartyB vault contains 1 states
    And node PartyA can run portfolio valuation
    And node PartyA portfolio valuation is <Valuation>
    And node PartyB portfolio valuation is <Valuation>

    Examples:
      | Corda-Node-Version | R3-Corda-Node-Version        | Cordapp-Name        | Valuation |
      | corda-3.0          | R3.CORDA-3.0.0-DEV-PREVIEW-3 | simm-valuation-demo | 12345     |
