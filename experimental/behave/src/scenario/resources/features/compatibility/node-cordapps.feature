@compatibility @node @cordapps
Feature: Compatibility - Mixed Corda distributions (OS and Enterprise) running different CorDapps
  To support an interoperable Corda network, different CorDapps must have the ability to transact in mixed Corda (OS) and R3 Corda (Enterprise) networks.

  Scenario Outline: Corda (OS) Node can transact with R3 Corda (Enterprise) node using Finance Cash application.
    Given a node A of version <Corda-Node-Version>
    And node A has the finance app installed
    And a node B of version <R3-Corda-Node-Version>
    And node B has the finance app installed
    And a nonvalidating notary N of version <Corda-Node-Version>
    When the network is ready
    Then node A can issue 1000 <Currency>
    And node A can transfer 100 <Currency> to node B

    Examples:
      | Corda-Node-Version    | R3-Corda-Node-Version   | Currency |
      | corda-3.0-RC01        | r3corda-3.0-DP2         | GBP      |

  Scenario Outline: Corda (OS) Node can transact with R3 Corda (Enterprise) node using the SIMM valuation demo.
    Given a node A of version <Corda-Node-Version> with proxy
    And node A has app installed: <Cordapp-Name>
    And a node B of version <R3-Corda-Node-Version>
    And node B has app installed: <Cordapp-Name>
    And a nonvalidating notary Notary of version <Corda-Node-Version>
    When the network is ready
    And node A has loaded app <Cordapp-Name>
    And node B has loaded app <Cordapp-Name>
    Then node A can trade with node B
    And node A vault contains 1 states
    And node B vault contains 1 states
#    And node A can run portfolio valuation
#    And node A portfolio valuation is <Valuation>
#    And node B portfolio valuation is <Valuation>

    Examples:
      | Corda-Node-Version       | R3-Corda-Node-Version   | Cordapp-Name        | Valuation |
      | corda-3.0-pre-release-V3 | r3-master               | simm-valuation-demo | 12345     |
