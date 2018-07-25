# Staged approach for introducing DDGs

## Stage one.

Use the confidential identities infrastructure to create a key and
certificate for the group. Store the group as a ContractState in the
vault.

The assumption at stage one is that only the creator of the group can
write to it.

Multiple states for the same data distribution group can be stored in
the vault. The issue here, though, is that the PartyAndCertificate is
duplicated for each one.