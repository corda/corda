@cash @issuance
Feature: Cash - Issuable Currencies
  To have cash on ledger, certain nodes must have the ability to issue cash of various currencies.

  Scenario Outline: Node can issue no currencies by default
    Given a node PartyA of version <Node-Version>
    And node PartyA has the finance app installed
    When the network is ready
    Then node PartyA has 0 issuable currencies

    Examples:
      | Node-Version    |
      | r3-master          |

  Scenario Outline: Node has an issuable currency
    Given a node PartyA of version <Node-Version>
    And node PartyA can issue currencies of denomination USD
    And node PartyA has the finance app installed
    When the network is ready
    Then node PartyA has 1 issuable currency

    Examples:
      | Node-Version    |
      | r3-master          |

  Scenario Outline: Node can issue a currency
    Given a node PartyA of version <Node-Version>
    And a nonvalidating notary Notary of version <Node-Version>
    And node PartyA has the finance app installed
    When the network is ready
    Then node PartyA can issue 100 USD

    Examples:
      | Node-Version    |
      | r3-master       |
