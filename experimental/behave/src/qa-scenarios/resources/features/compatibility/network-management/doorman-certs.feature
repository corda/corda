@compatibility @doorman
Feature: Compatibility - Doorman certificate issuance
  To support an interoperable Corda network, a Corda node must have the ability to transact with another Corda node
  when each node has been issued a certificate by a different Doorman version.

  Scenario Outline: Corda (OS) nodes can transact with each other, where they have been issued Certificates by different (R3 Corda) Doorman versions.
    Given a node A of version <Corda-Node-Version>
    And node A was issued a certificate by <Doorman-Version-X>
    And node A has the finance app installed
    And a node B of version <Corda-Node-Version>
    And node B was issued a certificate by <Doorman-Version-Y>
    And node B has the finance app installed
    When the network is ready
    Then node A can transfer 100 tokens to node B

  Examples:
      | Corda-Node-Version    | Doorman-Version-X       | Doorman-Version-Y     |
      | corda-3.0             | doorman-r3c-3.0-DP2     | doorman-r3c-3.0       |

  Scenario Outline: R3 Corda nodes can transact with each other, where they have been issued Certificates by different (R3 Corda) Doorman versions.
    Given a node A of version <Corda-Node-Version>
    And node A was issued a certificate by <Doorman-Version-X>
    And node A has the finance app installed
    And a node B of version <Corda-Node-Version>
    And node B was issued a certificate by <Doorman-Version-Y>
    And node B has the finance app installed
    When the network is ready
    Then node A can transfer 100 tokens to node B

    Examples:
      | Corda-Node-Version    | Doorman-Version-X       | Doorman-Version-Y     |
      | r3corda-3.0-DP2       | doorman-r3c-3.0-DP2     | doorman-r3c-3.0       |

  Scenario Outline: Mixed (R3 and OS) Corda nodes can transact with each other, where they have been issued Certificates by different (R3 Corda) Doorman versions.
    Given a node A of version <Corda-Node-Version-X>
    And node A was issued a certificate by <Doorman-Version-X>
    And node A has the finance app installed
    And a node B of version <Corda-Node-Version-Y>
    And node B was issued a certificate by <Doorman-Version-Y>
    And node B has the finance app installed
    When the network is ready
    Then node A can transfer 100 tokens to node B

    Examples:
      | Corda-Node-Version-X  | Corda-Node-Version-Y  | Doorman-Version-X       | Doorman-Version-Y     |
      | r3corda-3.0-DP2       | corda-3.0             | doorman-r3c-3.0-DP2     | doorman-r3c-3.0       |
