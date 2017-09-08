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

   .. sourcecode:: kotlin

        val partialTx = ...
        val oracle: Party = ...
        fun filtering(elem: Any): Boolean {
                return when (elem) {
                    is Command -> oracleParty.owningKey in elem.signers && elem.value is Fix
                    else -> false
                }
        }

Assuming that we already assembled partialTx with some commands and know the identity of Oracle service, we construct
filtering function over commands - ``filtering``. It performs type checking and filters only ``Fix`` commands as in
IRSDemo example. Then we can construct ``FilteredTransaction``:

.. container:: codeset

   .. sourcecode:: kotlin

        val wtx: WireTransaction = partialTx.toWireTransaction()
        val ftx: FilteredTransaction = wtx.buildFilteredTransaction(filtering)

In the Oracle example this step takes place in ``RatesFixFlow`` by overriding ``filtering`` function, see:
:ref:`filtering_ref`.

``FilteredTransaction`` holds ``filteredLeaves`` (data that we wanted to reveal) and Merkle branch for them.

.. container:: codeset

   .. sourcecode:: kotlin

        // Direct accsess to included commands, inputs, outputs, attachments etc.
        val cmds: List<Command> = ftx.filteredLeaves.commands
        val ins: List<StateRef> = ftx.filteredLeaves.inputs
        val timeWindow: TimeWindow? = ftx.filteredLeaves.timeWindow
        ...

.. literalinclude:: ../../samples/irs-demo/src/main/kotlin/net/corda/irs/api/NodeInterestRates.kt
    :language: kotlin
    :start-after: DOCSTART 1
    :end-before: DOCEND 1

Above code snippet is taken from ``NodeInterestRates.kt`` file and implements a signing part of an Oracle. You can
check only leaves using ``leaves.checkWithFun { check(it) }`` and then verify obtained ``FilteredTransaction`` to see
if data from ``PartialMerkleTree`` belongs to ``WireTransaction`` with provided ``id``. All you need is the root hash
of the full transaction:

.. container:: codeset

   .. sourcecode:: kotlin

        if (!ftx.verify(merkleRoot)){
                throw MerkleTreeException("Rate Fix Oracle: Couldn't verify partial Merkle tree.")
        }

Or combine the two steps together:

.. container:: codeset

   .. sourcecode:: kotlin

        ftx.verifyWithFunction(merkleRoot, ::check)

.. note:: The way the ``FilteredTransaction`` is constructed ensures that after signing of the root hash it's impossible to add or remove
    leaves. However, it can happen that having transaction with multiple commands one party reveals only subset of them to the Oracle.
    As signing is done now over the Merkle root hash, the service signs all commands of given type, even though it didn't see
    all of them. This issue will be handled after implementing partial signatures.
