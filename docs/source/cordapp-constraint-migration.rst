.. highlight:: kotlin
.. role:: kotlin(code)
    :language: kotlin
.. raw:: html


   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

CorDapp constraints migration
=============================

.. note:: Before reading this page, you should be familiar with the key concepts of :doc:`Contract Constraints <api-contract-constraints>`.

Faced with the exercise of upgrading an existing Corda 3.x CorDapp to Corda 4, you need to consider the following:

* What existing unconsumed states have been issued on ledger by a previous version of this CorDapp and using other constraint types?

   If you have existing **hash** constrained states see :ref:`Migrating hash constraints<hash_constraint_migration>`.

   If you have existing **CZ whitelisted** constrained states see :ref:`Migrating CZ whitelisted constraints<cz_whitelisted_constraint_migration>`.

   If you have existing **always accept** constrained states these are automatically consumable as they offer no security and should only
   be used in test environments.

* Should I use the **implicit** or **explicit** upgrade path?

   The general recommendation for Corda 4 is to use **implicit** upgrades for the reasons described :ref:`here <implicit_vs_explicit_upgrades>`.

   **Implicit** upgrades allow pre-authorising multiple implementations of the contract ahead of time.
   They do not require additional coding and do not incur a complex choreographed operational upgrade process.

.. _hash_constraint_migration:

Hash constraints migration
--------------------------

.. note:: These instructions only apply to CorDapp Contract JARs (unless otherwise stated).

Corda 4.0
~~~~~~~~~

Corda 4.0 requires some additional steps to consume pre-existing on-ledger **hash** constrained states:

1. The Corda Node must be started using relaxed hash constraint checking mode as described in :ref:`relax_hash_constraints_checking_ref`.

2. CorDapp flows that build transactions using pre-existing *hash-constrained* states must explicitly set output states to use *signature constraints*
   and specify the related public key(s) used in signing the associated CorDapp Contract JAR:

.. container:: codeset

    .. sourcecode:: kotlin

        // This will read the signers for the deployed CorDapp.
        val attachment = this.serviceHub.cordappProvider.getContractAttachmentID(contractClass)
        val signers = this.serviceHub.attachments.openAttachment(attachment!!)!!.signerKeys

        // Create the key that will have to pass for all future versions.
        val ownersKey = signers.first()

        val txBuilder = TransactionBuilder(notary)
                // Set the Signature constraint on the new state to migrate away from the hash constraint.
                .addOutputState(outputState, constraint = SignatureAttachmentConstraint(ownersKey))

    .. sourcecode:: java

        // This will read the signers for the deployed CorDapp.
        SecureHash attachment = this.getServiceHub().getCordappProvider().getContractAttachmentID(contractClass);
        List<PublicKey> signers = this.getServiceHub().getAttachments().openAttachment(attachment).getSignerKeys();

        // Create the key that will have to pass for all future versions.
        PublicKey ownersKey = signers.get(0);

        TransactionBuilder txBuilder = new TransactionBuilder(notary)
                // Set the Signature constraint on the new state to migrate away from the hash constraint.
                .addOutputState(outputState, myContract, new SignatureAttachmentConstraint(ownersKey))

3. Both the original pre-Corda 4 CorDapp JAR (generating *hash-constrained* states) and the new Corda 4 signed CorDapp JAR must be present in the
   Corda nodes /cordapps directory or imported manually via RPC using the ``uploadAttachment`` command (see
   :ref:`CorDapp Contract Attachments <cordapp_contract_attachments_ref>` for further information).


Later releases
~~~~~~~~~~~~~~

The next version of Corda will provide automatic migration of *hash constrained* states. This means that signed CorDapps running on a Corda 4.x node will
automatically consume any pre-existing on-ledger *hash-constrained* states (and generate *signature-constrained* outputs).

.. _cz_whitelisted_constraint_migration:

CZ whitelisted constraints migration
-------------------------------------

.. note:: These instructions only apply to CorDapp Contract JARs (unless otherwise stated).

Corda 4.0
~~~~~~~~~

Corda 4.0 requires some additional steps to consume pre-existing on-ledger **CZ whitelisted** constrained states:

1. As the original developer of the CorDapp, the first step is to sign the latest version of the JAR that was released (see :doc:`cordapp-build-systems`).
   The key used for signing will be used to sign all subsequent releases, so it should be stored appropriately. The JAR can be signed by multiple keys owned
   by different parties and it will be expressed as a ``CompositeKey`` in the ``SignatureAttachmentConstraint`` (See :doc:`api-core-types`).
   Use `JAR signing and verification tool <https://docs.oracle.com/javase/tutorial/deployment/jar/verify.html>`_ to sign the existing JAR.
   The signing capability of :ref:`corda-gradle-plugins <cordapp_build_system_signing_cordapp_jar_ref>` cannot be used in this context as it signs the JAR while building it from source.

2. Both the original pre-Corda 4 CorDapp JAR (generating *CZ whitelisted* constrained states) and the new Corda 4 signed CorDapp JAR must be
   registered with the CZ network operator (as whitelisted in the network parameters which are distributed to all nodes in that CZ).
   The CZ network operator should check that the JAR is signed and not allow any more versions of it to be whitelisted in the future.
   From now on the development organisation that signed the JAR is responsible for signing new versions.

   The process of CZ network CorDapp whitelisting depends on how the Corda network is configured:

    - if using a hosted CZ network (such as `The Corda Network <https://docs.corda.net/head/corda-network/index.html>`_ or
      `UAT Environment <https://docs.corda.net/head/corda-network/UAT.html>`_ ) running an Identity Operator (formerly known as Doorman) and
      Network Map Service, you should manually send the hashes of the two JARs to the CZ network operator and request these be added using
      their network parameter update process.

    - if using a local network created using the Network Bootstrapper tool, please follow the instructions in
      :ref:`Updating the contract whitelist for bootstrapped networks <bootstrapper_updating_whitelisted_contracts>` to can add both CorDapp Contract JAR hashes.

3. Any flows that build transactions using this CorDapp will have the responsibility of transitioning states to the ``SignatureAttachmentConstraint``.
   This is done explicitly in the code by setting the constraint of the output states to signers of the latest version of the whitelisted jar:

.. container:: codeset

    .. sourcecode:: kotlin

        // This will read the signers for the deployed CorDapp.
        val attachment = this.serviceHub.cordappProvider.getContractAttachmentID(contractClass)
        val signers = this.serviceHub.attachments.openAttachment(attachment!!)!!.signerKeys

        // Create the key that will have to pass for all future versions.
        val ownersKey = signers.first()

        val txBuilder = TransactionBuilder(notary)
                // Set the Signature constraint on the new state to migrate away from the WhitelistConstraint.
                .addOutputState(outputState, constraint = SignatureAttachmentConstraint(ownersKey))

    .. sourcecode:: java

        // This will read the signers for the deployed CorDapp.
        SecureHash attachment = this.getServiceHub().getCordappProvider().getContractAttachmentID(contractClass);
        List<PublicKey> signers = this.getServiceHub().getAttachments().openAttachment(attachment).getSignerKeys();

        // Create the key that will have to pass for all future versions.
        PublicKey ownersKey = signers.get(0);

        TransactionBuilder txBuilder = new TransactionBuilder(notary)
                // Set the Signature constraint on the new state to migrate away from the WhitelistConstraint.
                .addOutputState(outputState, myContract, new SignatureAttachmentConstraint(ownersKey))

4. As a node operator you need to add the new signed version of the contracts CorDapp to the "cordapps" folder together with the latest version of the flows jar.

Later releases
~~~~~~~~~~~~~~

The next version of Corda will provide automatic migration of *CZ whitelisted* constrained states. This means that signed CorDapps running on a Corda 4.x node will
automatically consume any pre-existing on-ledger *CZ whitelisted* constrained states (and generate *signature* constrained outputs).
