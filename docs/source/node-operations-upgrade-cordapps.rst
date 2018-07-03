Upgrading CorDapps on a node
============================

In order to upgrade a CorDapp on a node to a new version, it needs to be determined whether any backwards compatible
changes have been made. These could range from database changes, to changes in the protocol.

For developer information on upgrading CorDapps, see :doc:`upgrading-cordapps`.

To be compatible with Corda Enterprise, CorDapps need to bundle database migaration scripts (see :doc:`database-management`).


Flow upgrades
~~~~~~~~~~~~~

If any backwards-incompatible changes have been made (see :ref:`upgrading-cordapps-backwards-incompatible-flow-changes`
for more information), the upgrade method detailed below will need to be followed. Otherwise the CorDapp JAR can just
be replaced with the new version.

Contract and State upgrades
~~~~~~~~~~~~~~~~~~~~~~~~~~~

There are two types of contract/state upgrade:

1. *Implicit:* By allowing multiple implementations of the contract ahead of time, using constraints. See
   :doc:`api-contract-constraints` to learn more.
2. *Explicit:* By creating a special *contract upgrade transaction* and getting all participants of a state to sign it using the
   contract upgrade flows.

This documentation only considers the *explicit* type of upgrade, as implicit contract upgrades are handled by the application.

In an explicit upgrade contracts and states can be changed in arbitrary ways, if and only if all of the state's participants
agree to the proposed upgrade. The following combinations of upgrades are possible:

* A contract is upgraded while the state definition remains the same.
* A state is upgraded while the contract stays the same.
* The state and the contract are updated simultaneously.

Running the upgrade
~~~~~~~~~~~~~~~~~~~

If a contract or state requires an explicit upgrade then all states will need updating to the new contract at a time agreed
by all participants. The updated CorDapp JAR needs to be distributed to all relevant parties in advance of the changeover
time.

In order to perform the upgrade, follow the following steps:

* If required, do a flow drain to avoid the definition of states or contracts changing whilst a flow is in progress (see :ref:`upgrading-cordapps-flow-drains` for more information)

  * By RPC using the ``setFlowsDrainingModeEnabled`` method with the parameter ``true``
  * Via the shell by issuing the following command ``run setFlowsDrainingModeEnabled enabled: true``

* Check that all the flows have completed

  * By RPC using the ``stateMachinesSnapshot`` method and checking that there are no results
  * Via the shell by issuing the following command ``run stateMachinesSnapshot``

* Once all flows have completed, stop the node
* Replace the existing JAR with the new one
* Make any database changes required to any custom vault tables for the upgraded CorDapp
* Restart the node
* If you drained the node prior to upgrading, switch off flow draining mode to allow the node to continue to receive requests

  * By RPC using the ``setFlowsDrainingModeEnabled`` method with the parameter ``false``
  * Via the shell by issuing the following command ``run setFlowsDrainingModeEnabled enabled: false``

* Run the contract upgrade authorisation flow (``ContractUpgradeFlow$Initiate``) for each state that requires updating on every node.

  * You can do this manually via RPC but for anything more than a couple of states it is assumed that a script will be
    provided by the CorDapp developer to query the vault and run this for all states
  * The contract upgrade initiate flow only needs to be run on one of the participants for each state. The flow will
    automatically upgrade the state on all participants.
