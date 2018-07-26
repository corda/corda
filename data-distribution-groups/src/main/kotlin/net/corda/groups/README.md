# Staged approach for introducing DDGs

## Stage one.

* Groups currently have a name and key. The name is just a string.

* the confidential identities infrastructure added to the key management
  service is used to create a key and certificate for the group.

* The certificate is stored by the creator and sent to any invitees and
  so on.

* Groups are stored as ContractStates in the vault.

* The assumption at stage one is that only the creator of the group can
  write to it. Only the founder can add transactions to the group.

* Multiple states for the same data distribution group can be stored in
  the vault. Each state stores a bi-lateral group relationship apart
  from the initial group state which just contains the group founder.

* Inviting the same node twice can happen but doesn't have any impact
  as the flows deal in Sets of parties.

* Transactions are signed with the group key by the founder.

* Invitees check the signature on new transactions added to the group.
  They store all transactions anyway as currently the pre-packaged
  SendTransactionFlow and ReceiveTransactionFlows are used. This needs
  to change going forward.

* Transactions are propagated on to neighbours until there are no new
  neighbours to send the transactions to.

* There is currently no persistence of transaction which have been sent
  to a group.

* Only signed transactions can be added to a group.
