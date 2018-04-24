@compatibility @notary
Feature: Compatibility - Notary type change
  To support an interoperable Corda network, a Corda node using a Notary type must have the ability to transact with another Corda node
  using a different Notary type by changing Notarised states from one Notary type to another (by invoking notary change flow).

  Scenario Outline: Corda (OS) nodes can continue transacting with each other, using states that change from one Notary type to another (by invoking notary change flow)
    Given a node A of version <Corda-Node-Version> using <Notary-Type-A>
    And node A has the finance app installed
    And node A changes notary to <Notary-Type-B>
    And a node B of version <Corda-Node-Version> using <Notary-Type-B>
    And node B has the finance app installed
    When the network is ready
    Then node A can transfer 100 tokens to node B

    Examples:
      | Corda-Node-Version    | Notary-Type-A       | Notary-Type-B          |
      | corda-3.0             | notary-validating   | notary-non-validating  |

  Scenario Outline: Corda (OS) nodes can continue transacting with each other, using states that have been signed by different notaries (using transaction resolution).
    Given a node A of version <Corda-Node-Version> using <Notary-Type-A>
    And node A has the finance app installed
    And a node B of version <Corda-Node-Version> using <Notary-Type-B>
    And node B has the finance app installed
    When the network is ready
    Then node A can issue 1000 <Currency>
    And node A can transfer 100 <Currency> to node B

    Examples:
      | Corda-Node-Version    | Notary-Type-A       | Notary-Type-B          |
      | corda-3.0             | notary-validating   | notary-non-validating  |

  Scenario Outline: Unhappy path scenarios to be added.
    Examples: TODO

