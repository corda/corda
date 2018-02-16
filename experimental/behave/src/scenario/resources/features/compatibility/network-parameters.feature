@compatibility @networkparams @notary
Feature: Compatibility - CZ Network Parameters changes
  To support an interoperable Corda network, Corda nodes must continue transacting with each other
  when the CZ network parameters change: minimumPlatformVersion, notaries, maxMessageSize, maxTransactionSize,
  whitelistedContractImplementations.

  Scenario Outline: R3 Corda nodes continue transacting when list of Notaries changes (additive)
    Given a node A of version <Corda-Node-Version>
    And node A has the finance app installed
    And a node B of version <Corda-Node-Version>
    And node B has the finance app installed
    And doorman changes network parameters notaries
    When the network is ready
    Then node A can transfer 100 tokens to node B

  Examples:
      | Corda-Node-Version    |
      | r3corda-3.0           |

  Scenario Outline: R3 Corda nodes continue transacting when existing Notary changes from non-validating to validating
    Given a node A of version <Corda-Node-Version>
    And node A has the finance app installed
    And a node B of version <Corda-Node-Version>
    And node B has the finance app installed
    And doorman changes network parameters notary from non-validating to validating
    When the network is ready
    Then node A can transfer 100 tokens to node B

    Examples:
      | Corda-Node-Version    |
      | r3corda-3.0           |