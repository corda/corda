@cash @issuance
Feature: Cash - Issuable Currencies
  To have cash on ledger, certain nodes must have the ability to issue cash of various currencies.

  Scenario: Node can issue no currencies by default
    Given a node A of version master
    And node A has the finance app installed
    When the network is ready
    Then node A has 0 issuable currencies

  Scenario: Node can issue a currency
    Given a node A of version corda-3.0-HC02
    And node A can issue USD
    When the network is ready
    Then node A has 1 issuable currency

  Scenario: Node can issue a currency
    Given a node A of version corda-3.0-HC02
    And node A can issue USD
    When the network is ready
    Then node A has issued 100 USD