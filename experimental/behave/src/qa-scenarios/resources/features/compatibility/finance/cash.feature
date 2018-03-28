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
      | corda-3.0        | r3corda-3.0-DP3-RC01         | GBP      |

