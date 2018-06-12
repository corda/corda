# Enclave channels

AWS Lambdas may be invoked by name, and are simple request-response type RPCs. The lambda's name abstracts the 
specific JAR or code image that implements the functionality, which allows upgrading of a lambda without disrupting 
the rest of the lambdas.

Any authentication required for the invocation is done by a different AWS service (IAM), and is assumed to be taken 
care of by the time the lambda code is called.

Serverless enclaves also require ways to be addressed, let's call these "enclave channels". Each such channel may be 
identified with a string similar to Lambdas, however unlike lambdas we need to incorporate authentication into the 
concept of a channel in the form of attestation.

Furthermore unlike Lambdas we can implement a generic two-way communication channel. This reintroduces state into the 
enclave logic. However note that this state is in-memory only, and because of the transient nature of enclaves (they 
may be "lost" at any point) enclave authors are in general incentivised to either keep in-memory state minimal (by 
sealing state) or make their functionality idempotent (allowing retries).

We should be able to determine an enclave's supported channels statically. Enclaves may store this data for example in a
specific ELF section or a separate file. The latter may be preferable as it may be hard to have a central definition of
channels in an ELF section if we use JVM bytecode. Instead we could have a specific static JVM datastructure that can be
extracted from the enclave statically during the build.

## Sealed state

Sealing keys tied to specific CPUs seem to throw a wrench in the requirement of statelessness. Routing a request to an 
enclave that has associated sealed state cannot be the same as routing to one which doesn't. How can we transparently 
scale enclaves like Lambdas if fresh enclaves by definition don't have associated sealed state?

Take key provisioning as an example: we want some key to be accessible by a number of enclaves, how do we 
differentiate between enclaves that have the key provisioned versus ones that don't? We need to somehow expose an 
opaque version of the enclave's sealed state to the hosting infrastructure for this.

The way we could do this is by expressing this state in terms of a changing set of "active" enclave channels. The 
enclave can statically declare the channels it potentially supports, and start with some initial subset of them as 
active. As the enclave's lifecycle (sealed state) evolves it may change this active set to something different, 
thereby informing the hosting infrastructure that it shouldn't route certain requests there, or that it can route some 
other ones.

Take the above key provisioning example. An enclave can be in two states, unprovisioned or provisioned. When it's 
unprovisioned its set of active channels will be related to provisioning (for example, request to bootstrap key or 
request from sibling enclave), when it's provisioned its active set will be related to the usage of the key and 
provisioning of the key itself to unprovisioned enclaves.

The enclave's initial set of active channels defines how enclaves may be scaled horizontally, as these are the 
channels that will be active for the freshly started enclaves without sealed state.

"Hold on" you might say, "this means we didn't solve the scalability of stateful enclaves!".

This is partly true. However in the above case we can force certain channels to be part of the initial active set! In 
particular the channels that actually use the key (e.g. for signing) may be made "stateless" by lazily requesting 
provisioning of the key from sibling enclaves. Enclaves may be spun up on demand, and as long as there is at least one 
sibling enclave holding the key it will be provisioned as needed. This hints at a general pattern of hiding stateful
functionality behind stateless channels, if we want them to scale automatically.

Note that this doesn't mean we can't have external control over the provisioning of the key. For example we probably 
want to enforce redundancy across N CPUs. This requires the looping in of the hosting infrastructure, we cannot 
enforce this invariant purely in enclave code.

As we can see the set of active enclave channels are inherently tied to the sealed state of the enclave, therefore we 
should make the updating both of them an atomic operation.

### Side note

Another way to think about enclaves using sealed state is like an actor model. The sealed state is the actor's state,
and state transitions may be executed by any enclave instance running on the same CPU. By transitioning the actor state
one can also transition the type of messages the actor can receive atomically (= active channel set).

## Potential gRPC integration

It may be desirable to expose a built-in serialisation and network protocol. This would tie us to a specific protocol,
but in turn it would ease development.

An obvious candidate for this is gRPC as it supports streaming and a specific serialization protocol. We need to
investigate how we can integrate it so that channels are basically responsible for tunneling gRPC packets.
