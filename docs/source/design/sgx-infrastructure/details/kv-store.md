# Key-value store

To solve enclave to enclave and enclave to non-enclave communication we need a way to route requests correctly. There 
are readily available discovery solutions out there, however we have some special requirements because of the inherent 
statefulness of enclaves (route to enclave with correct state) and the dynamic nature of trust between them (route to 
enclave I can trust and that trusts me). To store metadata about discovery we can need some kind of distributed
key-value store.

The key-value store needs to store information about the following entities:
* Enclave image: measurement and supported channels
* Sealing identity: the sealing ID, the corresponding CPU ID and the host leasing it (if any)
* Sealed secret: the sealing ID, the sealing measurement, the sealed secret and corresponding active channel set
* Enclave deployment: mapping from channel to set of measurements
