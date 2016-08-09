.. highlight:: kotlin
.. role:: kotlin(code)
   :language: kotlin
.. raw:: html


   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Writing a contract test
=======================

This tutorial will take you through the steps required to write a contract test using Kotlin and/or Java.

The testing DSL allows one to define a piece of the ledger with transactions referring to each other, and ways of
verifying their correctness.

Testing single transactions
---------------------------

We start with the empty ledger:

.. container:: codeset

    .. sourcecode:: kotlin

        @Test
        fun emptyLedger() {
            ledger {
            }
        }

    .. sourcecode:: java

        import static com.r3corda.core.testing.JavaTestHelpers.*;
        import static com.r3corda.core.contracts.JavaTestHelpers.*;

        @Test
        public void emptyLedger() {
            ledger(l -> {
                return Unit.INSTANCE; // We need to return this explicitly
            });
        }

The DSL keyword ``ledger`` takes a closure that can build up several transactions and may verify their overall
correctness. A ledger is effectively a fresh world with no pre-existing transactions or services within it.

Let's add a Cash transaction:

.. container:: codeset

    .. sourcecode:: kotlin

        @Test
        fun simpleCashDoesntCompile() {
            val inState = Cash.State(
                    amount = 1000.DOLLARS `issued by` DUMMY_CASH_ISSUER,
                    owner = DUMMY_PUBKEY_1
            )
            ledger {
                transaction {
                    input(inState)
                }
            }
        }

    .. sourcecode:: java

        @Test
        public void simpleCashDoesntCompile() {
            Cash.State inState = new Cash.State(
                    issuedBy(DOLLARS(1000), getDUMMY_CASH_ISSUER()),
                    getDUMMY_PUBKEY_1()
            );
            ledger(l -> {
                l.transaction(tx -> {
                    tx.input(inState);
                });
                return Unit.INSTANCE;
            });
        }

We can add a transaction to the ledger using the ``transaction`` primitive. The transaction in turn may be defined by
specifying ``input``-s, ``output``-s, ``command``-s and ``attachment``-s.

The above ``input`` call is a bit special: Transactions don't actually contain input states, just references
to output states of other transactions. Under the hood the above ``input`` call creates a dummy transaction in the
ledger (that won't be verified) which outputs the specified state, and references that from this transaction.

The above code however doesn't compile:

.. container:: codeset

    .. sourcecode:: kotlin

        Error:(26, 21) Kotlin: Type mismatch: inferred type is Unit but EnforceVerifyOrFail was expected

    .. sourcecode:: java

        Error:(26, 31) java: incompatible types: bad return type in lambda expression missing return value

This is deliberate: The DSL forces us to specify either ``this.verifies()`` or ``this `fails with` "some text"`` on the
last line of ``transaction``:

.. container:: codeset

    .. sourcecode:: kotlin

        @Test
        fun simpleCash() {
            val inState = Cash.State(
                    amount = 1000.DOLLARS `issued by` MEGA_CORP.ref(1, 1),
                    owner = DUMMY_PUBKEY_1
            )
            ledger {
                transaction {
                    input(inState)
                    this.verifies()
                }
            }
        }

    .. sourcecode:: java

        @Test
        public void simpleCash() {
            Cash.State inState = new Cash.State(
                    issuedBy(DOLLARS(1000), getMEGA_CORP().ref((byte)1, (byte)1)),
                    getDUMMY_PUBKEY_1()
            );
            ledger(l -> {
                l.transaction(tx -> {
                    tx.input(inState);
                    return tx.verifies();
                });
                return Unit.INSTANCE;
            });
        }

The code finally compiles. When run, it produces the following error::

    com.r3corda.core.contracts.TransactionVerificationException$ContractRejection: java.lang.IllegalArgumentException: Failed requirement: for deposit [01] at issuer Snake Oil Issuer the amounts balance

.. note:: The reference here to the 'Snake Oil Issuer' is because we are using the pre-canned ``DUMMY_CASH_ISSUER``
    identity as the issuer of our cash.

The transaction verification failed, because the sum of inputs does not equal the sum of outputs. We can specify that
this is intended behaviour by changing ``this.verifies()`` to ``this `fails with` "the amounts balance"``:

.. container:: codeset

    .. sourcecode:: kotlin

        @Test
        fun simpleCashFailsWith() {
            val inState = Cash.State(
                    amount = 1000.DOLLARS `issued by` MEGA_CORP.ref(1, 1),
                    owner = DUMMY_PUBKEY_1
            )
            ledger {
                transaction {
                    input(inState)
                    this `fails with` "the amounts balance"
                }
            }
        }

    .. sourcecode:: java

        @Test
        public void simpleCashFailsWith() {
            Cash.State inState = new Cash.State(
                    issuedBy(DOLLARS(1000), getMEGA_CORP().ref((byte)1, (byte)1)),
                    getDUMMY_PUBKEY_1()
            );
            ledger(l -> {
                l.transaction(tx -> {
                    tx.input(inState);
                    return tx.failsWith("the amounts balance");
                });
                return Unit.INSTANCE;
            });
        }

We can continue to build the transaction until it ``verifies``:

.. container:: codeset

    .. sourcecode:: kotlin

        @Test
        fun simpleCashSuccess() {
            val inState = Cash.State(
                    amount = 1000.DOLLARS `issued by` MEGA_CORP.ref(1, 1),
                    owner = DUMMY_PUBKEY_1
            )
            ledger {
                transaction {
                    input(inState)
                    this `fails with` "the amounts balance"
                    output(inState.copy(owner = DUMMY_PUBKEY_2))
                    command(DUMMY_PUBKEY_1) { Cash.Commands.Move() }
                    this.verifies()
                }
            }
        }

    .. sourcecode:: java

        @Test
        public void simpleCashSuccess() {
            Cash.State inState = new Cash.State(
                    issuedBy(DOLLARS(1000), getMEGA_CORP().ref((byte)1, (byte)1)),
                    getDUMMY_PUBKEY_1()
            );
            ledger(l -> {
                l.transaction(tx -> {
                    tx.input(inState);
                    tx.failsWith("the amounts balance");
                    tx.output(inState.copy(inState.getAmount(), getDUMMY_PUBKEY_2()));
                    tx.command(getDUMMY_PUBKEY_1(), new Cash.Commands.Move());
                    return tx.verifies();
                });
                return Unit.INSTANCE;
            });
        }

``output`` specifies that we want the input state to be transferred to ``DUMMY_PUBKEY_2`` and ``command`` adds the
``Move`` command itself, signed by the current owner of the input state, ``DUMMY_PUBKEY_1``.

We constructed a complete signed cash transaction from ``DUMMY_PUBKEY_1`` to ``DUMMY_PUBKEY_2`` and verified it. Note
how we left in the ``fails with`` line - this is fine, the failure will be tested on the partially constructed
transaction.

What should we do if we wanted to test what happens when the wrong party signs the transaction? If we simply add a
``command`` it will ruin the transaction for good... Enter ``tweak``:

.. container:: codeset

    .. sourcecode:: kotlin

        @Test
        fun simpleCashTweakSuccess() {
            val inState = Cash.State(
                    amount = 1000.DOLLARS `issued by` MEGA_CORP.ref(1, 1),
                    owner = DUMMY_PUBKEY_1
            )
            ledger {
                transaction {
                    input(inState)
                    this `fails with` "the amounts balance"
                    output(inState.copy(owner = DUMMY_PUBKEY_2))

                    tweak {
                        command(DUMMY_PUBKEY_2) { Cash.Commands.Move() }
                        this `fails with` "the owning keys are the same as the signing keys"
                    }

                    command(DUMMY_PUBKEY_1) { Cash.Commands.Move() }
                    this.verifies()
                }
            }
        }

    .. sourcecode:: java

        @Test
        public void simpleCashTweakSuccess() {
            Cash.State inState = new Cash.State(
                    issuedBy(DOLLARS(1000), getMEGA_CORP().ref((byte)1, (byte)1)),
                    getDUMMY_PUBKEY_1()
            );
            ledger(l -> {
                l.transaction(tx -> {
                    tx.input(inState);
                    tx.failsWith("the amounts balance");
                    tx.output(inState.copy(inState.getAmount(), getDUMMY_PUBKEY_2()));

                    tx.tweak(tw -> {
                        tw.command(getDUMMY_PUBKEY_2(), new Cash.Commands.Move());
                        return tw.failsWith("the owning keys are the same as the signing keys");
                    });
                    tx.command(getDUMMY_PUBKEY_1(), new Cash.Commands.Move());
                    return tx.verifies();
                });
                return Unit.INSTANCE;
            });
        }

``tweak`` creates a local copy of the transaction. This allows the local "ruining" of the transaction allowing testing
of different error conditions.

We now have a neat little test that tests a single transaction. This is already useful, and in fact testing of a single
transaction in this way is very common. There is even a shorthand toplevel ``transaction`` primitive that creates a
ledger with a single transaction:

.. container:: codeset

    .. sourcecode:: kotlin

        @Test
        fun simpleCashTweakSuccessTopLevelTransaction() {
            val inState = Cash.State(
                    amount = 1000.DOLLARS `issued by` MEGA_CORP.ref(1, 1),
                    owner = DUMMY_PUBKEY_1
            )
            transaction {
                input(inState)
                this `fails with` "the amounts balance"
                output(inState.copy(owner = DUMMY_PUBKEY_2))

                tweak {
                    command(DUMMY_PUBKEY_2) { Cash.Commands.Move() }
                    this `fails with` "the owning keys are the same as the signing keys"
                }

                command(DUMMY_PUBKEY_1) { Cash.Commands.Move() }
                this.verifies()
            }
        }

    .. sourcecode:: java

        @Test
        public void simpleCashTweakSuccessTopLevelTransaction() {
            Cash.State inState = new Cash.State(
                    issuedBy(DOLLARS(1000), getMEGA_CORP().ref((byte)1, (byte)1)),
                    getDUMMY_PUBKEY_1()
            );
            transaction(tx -> {
                tx.input(inState);
                tx.failsWith("the amounts balance");
                tx.output(inState.copy(inState.getAmount(), getDUMMY_PUBKEY_2()));

                tx.tweak(tw -> {
                    tw.command(getDUMMY_PUBKEY_2(), new Cash.Commands.Move());
                    return tw.failsWith("the owning keys are the same as the signing keys");
                });
                tx.command(getDUMMY_PUBKEY_1(), new Cash.Commands.Move());
                return tx.verifies();
            });
        }

Chaining transactions
---------------------

Now that we know how to define a single transaction, let's look at how to define a chain of them:

.. container:: codeset

    .. sourcecode:: kotlin

        @Test
        fun chainCash() {
            ledger {
                unverifiedTransaction {
                    output("MEGA_CORP cash") {
                        Cash.State(
                                amount = 1000.DOLLARS `issued by` MEGA_CORP.ref(1, 1),
                                owner = MEGA_CORP_PUBKEY
                        )
                    }
                }

                transaction {
                    input("MEGA_CORP cash")
                    output("MEGA_CORP cash".output<Cash.State>().copy(owner = DUMMY_PUBKEY_1))
                    command(MEGA_CORP_PUBKEY) { Cash.Commands.Move() }
                    this.verifies()
                }
            }
        }

    .. sourcecode:: java

        @Test
        public void chainCash() {
            ledger(l -> {
                l.unverifiedTransaction(tx -> {
                    tx.output("MEGA_CORP cash",
                            new Cash.State(
                                    issuedBy(DOLLARS(1000), getMEGA_CORP().ref((byte)1, (byte)1)),
                                    getMEGA_CORP_PUBKEY()
                            )
                    );
                    return Unit.INSTANCE;
                });

                l.transaction(tx -> {
                    tx.input("MEGA_CORP cash");
                    Cash.State inputCash = l.retrieveOutput(Cash.State.class, "MEGA_CORP cash");
                    tx.output(inputCash.copy(inputCash.getAmount(), getDUMMY_PUBKEY_1()));
                    tx.command(getMEGA_CORP_PUBKEY(), new Cash.Commands.Move());
                    return tx.verifies();
                });

                return Unit.INSTANCE;
            });
        }

In this example we declare that ``MEGA_CORP`` has a thousand dollars but we don't care where from, for this we can use
``unverifiedTransaction``. Note how we don't need to specify ``this.verifies()``.

The ``output`` cash was labelled with ``"MEGA_CORP cash"``, we can subsequently referred to this other transactions, e.g.
by ``input("MEGA_CORP cash")`` or ``"MEGA_CORP cash".output<Cash.State>()``.

What happens if we reuse the output cash twice?

.. container:: codeset

    .. sourcecode:: kotlin

        @Test
        fun chainCashDoubleSpend() {
            ledger {
                unverifiedTransaction {
                    output("MEGA_CORP cash") {
                        Cash.State(
                                amount = 1000.DOLLARS `issued by` MEGA_CORP.ref(1, 1),
                                owner = MEGA_CORP_PUBKEY
                        )
                    }
                }

                transaction {
                    input("MEGA_CORP cash")
                    output("MEGA_CORP cash".output<Cash.State>().copy(owner = DUMMY_PUBKEY_1))
                    command(MEGA_CORP_PUBKEY) { Cash.Commands.Move() }
                    this.verifies()
                }

                transaction {
                    input("MEGA_CORP cash")
                    // We send it to another pubkey so that the transaction is not identical to the previous one
                    output("MEGA_CORP cash".output<Cash.State>().copy(owner = DUMMY_PUBKEY_2))
                    command(MEGA_CORP_PUBKEY) { Cash.Commands.Move() }
                    this.verifies()
                }
            }
        }

    .. sourcecode:: java

        @Test
        public void chainCashDoubleSpend() {
            ledger(l -> {
                l.unverifiedTransaction(tx -> {
                    tx.output("MEGA_CORP cash",
                            new Cash.State(
                                    issuedBy(DOLLARS(1000), getMEGA_CORP().ref((byte)1, (byte)1)),
                                    getMEGA_CORP_PUBKEY()
                            )
                    );
                    return Unit.INSTANCE;
                });

                l.transaction(tx -> {
                    tx.input("MEGA_CORP cash");
                    Cash.State inputCash = l.retrieveOutput(Cash.State.class, "MEGA_CORP cash");
                    tx.output(inputCash.copy(inputCash.getAmount(), getDUMMY_PUBKEY_1()));
                    tx.command(getMEGA_CORP_PUBKEY(), new Cash.Commands.Move());
                    return tx.verifies();
                });

                l.transaction(tx -> {
                    tx.input("MEGA_CORP cash");
                    Cash.State inputCash = l.retrieveOutput(Cash.State.class, "MEGA_CORP cash");
                    // We send it to another pubkey so that the transaction is not identical to the previous one
                    tx.output(inputCash.copy(inputCash.getAmount(), getDUMMY_PUBKEY_2()));
                    tx.command(getMEGA_CORP_PUBKEY(), new Cash.Commands.Move());
                    return tx.verifies();
                });

                return Unit.INSTANCE;
            });
        }

The transactions ``verifies()`` individually, however the state was spent twice!

We can also verify the complete ledger by calling ``verifies``/``fails`` on the ledger level. We can also use
``tweak`` to create a local copy of the whole ledger:

.. container:: codeset

    .. sourcecode:: kotlin

        @Test
        fun chainCashDoubleSpendFailsWith() {
            ledger {
                unverifiedTransaction {
                    output("MEGA_CORP cash") {
                        Cash.State(
                                amount = 1000.DOLLARS `issued by` MEGA_CORP.ref(1, 1),
                                owner = MEGA_CORP_PUBKEY
                        )
                    }
                }

                transaction {
                    input("MEGA_CORP cash")
                    output("MEGA_CORP cash".output<Cash.State>().copy(owner = DUMMY_PUBKEY_1))
                    command(MEGA_CORP_PUBKEY) { Cash.Commands.Move() }
                    this.verifies()
                }

                tweak {
                    transaction {
                        input("MEGA_CORP cash")
                        // We send it to another pubkey so that the transaction is not identical to the previous one
                        output("MEGA_CORP cash".output<Cash.State>().copy(owner = DUMMY_PUBKEY_1))
                        command(MEGA_CORP_PUBKEY) { Cash.Commands.Move() }
                        this.verifies()
                    }
                    this.fails()
                }

                this.verifies()
            }
        }

    .. sourcecode:: java

        @Test
        public void chainCashDoubleSpendFailsWith() {
            ledger(l -> {
                l.unverifiedTransaction(tx -> {
                    tx.output("MEGA_CORP cash",
                            new Cash.State(
                                    issuedBy(DOLLARS(1000), getMEGA_CORP().ref((byte)1, (byte)1)),
                                    getMEGA_CORP_PUBKEY()
                            )
                    );
                    return Unit.INSTANCE;
                });

                l.transaction(tx -> {
                    tx.input("MEGA_CORP cash");
                    Cash.State inputCash = l.retrieveOutput(Cash.State.class, "MEGA_CORP cash");
                    tx.output(inputCash.copy(inputCash.getAmount(), getDUMMY_PUBKEY_1()));
                    tx.command(getMEGA_CORP_PUBKEY(), new Cash.Commands.Move());
                    return tx.verifies();
                });

                l.tweak(lw -> {
                    lw.transaction(tx -> {
                        tx.input("MEGA_CORP cash");
                        Cash.State inputCash = l.retrieveOutput(Cash.State.class, "MEGA_CORP cash");
                        // We send it to another pubkey so that the transaction is not identical to the previous one
                        tx.output(inputCash.copy(inputCash.getAmount(), getDUMMY_PUBKEY_2()));
                        tx.command(getMEGA_CORP_PUBKEY(), new Cash.Commands.Move());
                        return tx.verifies();
                    });
                    lw.fails();
                    return Unit.INSTANCE;
                });

                l.verifies();
                return Unit.INSTANCE;
            });
        }
