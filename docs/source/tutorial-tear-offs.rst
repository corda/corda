.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Transaction tear-offs
=====================

Suppose we want to construct a transaction that includes commands containing interest rate fix data as in
:doc:`oracles`. Before sending the transaction to the oracle to obtain its signature, we need to filter out every part
of the transaction except for the ``Fix`` commands.

To do so, we need to create a filtering function that specifies which fields of the transaction should be included.
Each field will only be included if the filtering function returns `true` when the field is passed in as input.

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/tutorial/tearoffs/TutorialTearOffs.kt
        :language: kotlin
        :start-after: DOCSTART 1
        :end-before: DOCEND 1
        :dedent: 4

We can now use our filtering function to construct a ``FilteredTransaction``:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/tutorial/tearoffs/TutorialTearOffs.kt
        :language: kotlin
        :start-after: DOCSTART 2
        :end-before: DOCEND 2
        :dedent: 4

In the Oracle example this step takes place in ``RatesFixFlow`` by overriding the ``filtering`` function. See
:ref:`filtering_ref`.

Both ``WireTransaction`` and ``FilteredTransaction`` inherit from ``TraversableTransaction``, so access to the
transaction components is exactly the same. Note that unlike ``WireTransaction``,
``FilteredTransaction`` only holds data that we wanted to reveal (after filtering).

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/tutorial/tearoffs/TutorialTearOffs.kt
        :language: kotlin
        :start-after: DOCSTART 3
        :end-before: DOCEND 3
        :dedent: 4

The following code snippet is taken from the IRS Demo and implements a signing part of an Oracle.

.. container:: codeset

    .. code-block:: kotlin

            fun sign(ftx: FilteredTransaction): TransactionSignature {
                ftx.verify()
                // Performing validation of obtained filtered components.
                fun commandValidator(elem: Command<*>): Boolean {
                    require(services.myInfo.legalIdentities.first().owningKey in elem.signers && elem.value is Fix) {
                        "Oracle received unknown command (not in signers or not Fix)."
                    }
                    val fix = elem.value as Fix
                    val known = knownFixes[fix.of]
                    if (known == null || known != fix)
                        throw UnknownFix(fix.of)
                    return true
                }

                fun check(elem: Any): Boolean {
                    return when (elem) {
                        is Command<*> -> commandValidator(elem)
                        else -> throw IllegalArgumentException("Oracle received data of different type than expected.")
                    }
                }

                require(ftx.checkWithFun(::check))
                ftx.checkCommandVisibility(services.myInfo.legalIdentities.first().owningKey)
                // It all checks out, so we can return a signature.
                //
                // Note that we will happily sign an invalid transaction, as we are only being presented with a filtered
                // version so we can't resolve or check it ourselves. However, that doesn't matter much, as if we sign
                // an invalid transaction the signature is worthless.
                return services.createSignature(ftx, services.myInfo.legalIdentities.first().owningKey)
            }

.. note:: The way the ``FilteredTransaction`` is constructed ensures that after signing of the root hash it's impossible to add or remove
    components (leaves). However, it can happen that having transaction with multiple commands one party reveals only subset of them to the Oracle.
    As signing is done now over the Merkle root hash, the service signs all commands of given type, even though it didn't see
    all of them. In the case however where all of the commands should be visible to an Oracle, one can type ``ftx.checkAllComponentsVisible(COMMANDS_GROUP)`` before invoking ``ftx.verify``.
    ``checkAllComponentsVisible`` is using a sophisticated underlying partial Merkle tree check to guarantee that all of
    the components of a particular group that existed in the original ``WireTransaction`` are included in the received
    ``FilteredTransaction``.