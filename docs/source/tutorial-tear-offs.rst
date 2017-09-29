Transaction tear-offs
=====================

Example of usage
----------------
Let’s focus on a code example. We want to construct a transaction with commands containing interest rate fix data as in:
:doc:`oracles`. After construction of a partial transaction, with included ``Fix`` commands in it, we want to send it
to the Oracle for checking and signing. To do so we need to specify which parts of the transaction are going to be
revealed. That can be done by constructing filtering function over fields of ``WireTransaction`` of type ``(Any) ->
Boolean``.

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/tutorial/tearoffs/TutorialTearOffs.kt
        :language: kotlin
        :start-after: DOCSTART 1
        :end-before: DOCEND 1

Assuming that we already assembled partialTx with some commands and know the identity of Oracle service, we construct
filtering function over commands - ``filtering``. It performs type checking and filters only ``Fix`` commands as in
IRSDemo example. Then we can construct ``FilteredTransaction``:

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/tutorial/tearoffs/TutorialTearOffs.kt
        :language: kotlin
        :start-after: DOCSTART 2
        :end-before: DOCEND 2

In the Oracle example this step takes place in ``RatesFixFlow`` by overriding ``filtering`` function, see:
:ref:`filtering_ref`.

Both ``WireTransaction`` and ``FilteredTransaction`` inherit from ``TraversableTransaction``, so access to the
transaction components is exactly the same. Note that unlike ``WireTransaction``,
``FilteredTransaction`` only holds data that we wanted to reveal (after filtering).

.. container:: codeset

    .. literalinclude:: ../../docs/source/example-code/src/main/kotlin/net/corda/docs/tutorial/tearoffs/TutorialTearOffs.kt
        :language: kotlin
        :start-after: DOCSTART 3
        :end-before: DOCEND 3

The following code snippet is taken from ``NodeInterestRates.kt`` and implements a signing part of an Oracle.

.. literalinclude:: ../../samples/irs-demo/src/main/kotlin/net/corda/irs/api/NodeInterestRates.kt
    :language: kotlin
    :start-after: DOCSTART 1
    :end-before: DOCEND 1
    :dedent: 8

.. note:: The way the ``FilteredTransaction`` is constructed ensures that after signing of the root hash it's impossible
to add or remove components (leaves). However, it can happen that having transaction with multiple commands one party
reveals only subset of them to the Oracle. As signing is done now over the Merkle root hash, the service signs all
commands of given type, even though it didn't see all of them. In the case however where all of the commands should be
visible to an Oracle, one can type ``ftx.checkAllComponentsVisible(COMMANDS_GROUP)`` before invoking ``ftx.verify``.
``checkAllComponentsVisible`` is using a sophisticated underlying partial Merkle tree check to guarantee that all of
the components of a particular group that existed in the original ``WireTransaction`` are included in the received
``FilteredTransaction``.
