Network verifier
----------------

Simple CorDapp that can be used to verify the setup of a Corda network.
It contacts every other network participant and receives a reply from them.
It also creates a transaction and finalizes it.

This makes sure that all basic Corda functionality works.

*Usage:*

- From the rpc just run the ``TestCommsFlowInitiator`` flow and inspect the result. There should be a "Hello" message from every ohter participant.
 