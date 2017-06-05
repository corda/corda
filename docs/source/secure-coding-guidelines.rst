Secure coding guidelines
========================

The platform does what it can to be secure by default and safe by design. Unfortunately the platform cannot
prevent every kind of security mistake. This document describes what to think about when writing applications
to block various kinds of attack. Whilst it may be tempting to just assume no reasonable counterparty would
attempt to subvert your trades using flow level attacks, relying on trust for software security makes it
harder to scale up your operations later when you might want to add counterparties quickly and without
extensive vetting.

Flows
-----

:doc:`flow-state-machines` are how your app communicates with other parties on the network. Therefore they
are the typical entry point for malicious data into your app and must be treated with care.

The ``receive`` methods return data wrapped in the ``UntrustworthyData<T>`` marker type. This type doesn't add
any functionality, it's only there to remind you to properly validate everything that you get from the network.
Remember that the other side may *not* be running the code you provide to take part in the flow: they are
allowed to do anything! Things to watch out for:

* A transaction that doesn't match a partial transaction built or proposed earlier in the flow, for instance,
  if you propose to trade a cash state worth $100 for an asset, and the transaction to sign comes back from the
  other side, you must check that it points to the state you actually requested. Otherwise the attacker could
  get you to sign a transaction that spends a much larger state to you, if they know the ID of one!
* A transaction that isn't of the right type. There are two transaction types: general and notary change. If you
  are expecting one type but get the other you may find yourself signing a transaction that transfers your assets
  to the control of a hostile notary.
* Unexpected changes in any part of the states in a transaction. If you have access to all the needed data, you
  could re-run the builder logic and do a comparison of the resulting states to ensure that it's what you expected.
  For instance if the data needed to construct the next state is available to both parties, the function to
  calculate the transaction you want to mutually agree could be shared between both classes implementing both
  sides of the flow.

The theme should be clear: signing is a very sensitive operation, so you need to be sure you know what it is you
are about to sign, and that nothing has changed in the small print! Once you have provided your signature over a
transaction to a counterparty, there is no longer anything you can do to prevent them from committing it to the ledger.

Contracts
---------

Contracts are arbitrary functions inside a JVM sandbox and therefore they have a lot of leeway to shoot themselves
in the foot. Things to watch out for:

* Changes in states that should not be allowed by the current state transition. You will want to check that no
  fields are changing except the intended fields!
* Accidentally catching and discarding exceptions that might be thrown by validation logic.
* Calling into other contracts via virtual methods if you don't know what those other contracts are or might do.
