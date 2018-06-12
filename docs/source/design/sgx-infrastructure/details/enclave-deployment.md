# Enclave deployment

What happens if we roll out a new enclave image?

In production we need to sign the image directly with the R3 key as MRSIGNER (process to be designed), as well as create
any whitelisting signatures needed (e.g. from auditors) in order to allow existing enclaves to trust the new one.

We need to make the enclave build sources available to users - we can package this up as a single container pinning all
build dependencies and source code. Docker style image layering/caching will come in handy here.

Once the image, build containers and related signatures are created we need to push this to the main R3 enclave storage.

Enclave infrastructure owners (e.g. Corda nodes) may then start using the images depending on their upgrade policy. This
involves updating their key value store so that new channel discovery requests resolve to the new measurement, which in
turn will trigger the image download on demand on enclave hosts. We can potentially add pre-caching here to reduce
latency for first-time enclave users.
