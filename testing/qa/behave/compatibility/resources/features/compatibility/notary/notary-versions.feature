@compatibility @notary
Feature: Compatibility - Notary version change
  To support an interoperable Corda network, a Corda node must have the ability to transact with another Corda node
  using notarised states from a different Notary version.

  Scenario Outline: Corda (OS) nodes can continue transacting with each other, where states have been notarised by different versions of a notary
    Given a node A of version <Corda-Node-Version>
    And node A has the finance app installed
    And a node B of version <Corda-Node-Version>
    And node B has the finance app installed
    When the network is ready
    Then node A can transfer 100 tokens to node B using <Notary-Version-X>
    And node B can transfer 100 tokens to node A using <Notary-Version-Y>

    Examples:
      | Corda-Node-Version    | Notary-Version-X       | Notary-Version-Y     |
      | corda-3.0             | notary-r3c-3.0-DP2     | notary-r3c-3.0       |
