Running a notary service
------------------------

At present we have several notary implementations:

1. ``SimpleNotaryService`` (single node) -- commits the provided transaction input states without any validation.
2. ``ValidatingNotaryService`` (single node) -- retrieves and validates the whole transaction history
   (including the given transaction) before committing.
3. ``RaftNonValidatingNotaryService`` (distributed) -- functionally equivalent to ``SimpleNotaryService``, but stores
   the committed states in a distributed collection replicated and persisted in a Raft cluster. For the consensus layer
   we are using the `Copycat <http://atomix.io/copycat/>`_ framework.
4. ``RaftValidatingNotaryService`` (distributed) -- as above, but performs validation on the transactions received.

To have a node run a notary service, you need to set appropriate ``notary`` configuration before starting it
(see :doc:`corda-configuration-file` for reference).

For ``SimpleNotaryService`` the config is simply:

.. parsed-literal::

    notary : { validating : false }

For ``ValidatingNotaryService``, it is:

.. parsed-literal::

    notary : { validating : true }

Setting up a Raft notary is currently slightly more involved and is not recommended for prototyping purposes. There is
work in progress to simplify it. To see it in action, however, you can try out the :ref:`notary-demo`.

Use the `--bootstrap-raft-cluster` command line argument when starting the first node of a notary cluster for the first
time. When the flag is set, the node will act as a seed for the cluster that other members can join.
