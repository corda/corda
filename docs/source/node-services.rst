Node services
=============

This document is intended as a very brief introduction to the current 
service components inside the node. Whilst not at all exhaustive it is 
hoped that this will give some context when writing applications and 
code that use these services, or which are operated upon by the internal 
components of Corda. 

Services within the node
------------------------

The node services represent the various sub functions of the Corda node. 
Some are directly accessible to contracts and flows through the 
``ServiceHub``, whilst others are the framework internals used to host 
the node functions. Any public service interfaces are defined in the 
``net.corda.core.node.services`` package. The ``ServiceHub`` interface exposes
functionality suitable for flows.
The implementation code for all standard services lives in the ``net.corda.node.services`` package.

All the services are constructed in the ``AbstractNode`` ``start`` 
method. They may also register a shutdown handler during initialisation,
which will be called in reverse order to the start registration sequence when the ``Node.stop`` is called.

The roles of the individual services are described below.

Key management and identity services
------------------------------------

InMemoryIdentityService
~~~~~~~~~~~~~~~~~~~~~~~

The ``InMemoryIdentityService`` implements the ``IdentityService`` 
interface and provides a store of remote mappings between ``PublicKey``
and remote ``Parties``. It is automatically populated from the 
``NetworkMapCache`` updates and is used when translating ``PublicKey``
exposed in transactions into fully populated ``Party`` identities. This 
service is also used in the default JSON mapping of parties in the web 
server, thus allowing the party names to be used to refer to other nodes' 
legal identities. In the future the Identity service will be made 
persistent and extended to allow anonymised session keys to be used in 
flows where the well-known ``PublicKey`` of nodes need to be hidden
to non-involved parties. 

PersistentKeyManagementService and E2ETestKeyManagementService
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Typical usage of these services is to locate an appropriate 
``PrivateKey`` to complete and sign a verified transaction as part of a 
flow. The normal node legal identifier keys are typically accessed via 
helper extension methods on the ``ServiceHub``, but these ultimately delegate
signing to internal ``PrivateKeys`` from the ``KeyManagementService``. The
``KeyManagementService`` interface also allows other keys to be 
generated if anonymous keys are needed in a flow. Note that this 
interface works at the level of individual ``PublicKey`` and internally
matched ``PrivateKey` pairs, but the signing authority may be represented by a 
``CompositeKey`` on the ``NodeInfo`` to allow key clustering and 
threshold schemes. 

The ``PersistentKeyManagementService`` is a persistent implementation of 
the ``KeyManagementService`` interface that records the key pairs to a 
key-value storage table in the database. ``E2ETestKeyManagementService`` 
is a simple implementation of the ``KeyManagementService`` that is used 
to track our ``KeyPairs`` for use in unit testing when no database is 
available. 

Messaging and network management services
-----------------------------------------

ArtemisMessagingServer
~~~~~~~~~~~~~~~~~~~~~~

The ``ArtemisMessagingServer`` service is run internally by the Corda 
node to host the ``ArtemisMQ`` messaging broker that is used for 
reliable node communications. Although the node can be configured to 
disable this and connect to a remote broker by setting the 
``messagingServerAddress`` configuration to be the remote broker 
address. (The ``MockNode`` used during testing does not use this 
service, and has a simplified in-memory network layer instead.) This 
service is not exposed to any CorDapp code as it is an entirely internal 
infrastructural component. However, the developer may need to be aware 
of this component, because the ``ArtemisMessagingServer`` is responsible 
for configuring the network ports (based upon settings in ``node.conf``) 
and the service configures the security settings of the ``ArtemisMQ`` 
middleware and acts to form bridges between node mailbox queues based 
upon connection details advertised by the ``NetworkMapCache``. The
``ArtemisMQ`` broker is configured to use TLS1.2 with a custom 
``TrustStore`` containing a Corda root certificate and a ``KeyStore`` 
with a certificate and key signed by a chain back to this root 
certificate. These keystores typically reside in the ``certificates`` 
sub folder of the node workspace. For the nodes to be able to connect to 
each other it is essential that the entire set of nodes are able to 
authenticate against each other and thus typically that they share a 
common root certificate. Also note that the address configuration 
defined for the server is the basis for the address advertised in the 
``NetworkMapCache`` and thus must be externally connectable by all nodes
in the network. 

P2PMessagingClient
~~~~~~~~~~~~~~~~~~

The ``P2PMessagingClient`` is the implementation of the
``MessagingService`` interface operating across the ``ArtemisMQ`` 
middleware layer. It typically connects to the local ``ArtemisMQ`` 
hosted within the ``ArtemisMessagingServer`` service. However, the 
``messagingServerAddress`` configuration can be set to a remote broker 
address if required. The responsibilities of this service include 
managing the node's persistent mailbox, sending messages to remote peer 
nodes, acknowledging properly consumed messages and deduplicating any 
resent messages. The service also handles the incoming requests from new 
RPC client sessions and hands them to the ``CordaRPCOpsImpl`` to carry 
out the requests. 

InMemoryNetworkMapCache
~~~~~~~~~~~~~~~~~~~~~~~

The ``InMemoryNetworkMapCache`` implements the ``NetworkMapCache`` 
interface and is responsible for tracking the identities and advertised 
services of authorised nodes provided by the remote 
``NetworkMapService``. Typical use is to search for nodes hosting 
specific advertised services e.g. a Notary service, or an Oracle 
service. Also, this service allows mapping of friendly names, or 
``Party`` identities to the full ``NodeInfo`` which is used in the 
``StateMachineManager`` to convert between the ``PublicKey``, or
``Party`` based addressing used in the flows/contracts and the 
physical host and port information required for the physical 
``ArtemisMQ`` messaging layer.

Storage and persistence related services
----------------------------------------

DBCheckpointStorage
~~~~~~~~~~~~~~~~~~~

The ``DBCheckpointStorage`` service is used from within the 
``StateMachineManager`` code to persist the progress of flows. Thus 
ensuring that if the program terminates the flow can be restarted 
from the same point and complete the flow. This service should not 
be used by any CorDapp components. 

DBTransactionMappingStorage and InMemoryStateMachineRecordedTransactionMappingStorage
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The ``DBTransactionMappingStorage`` is used within the 
``StateMachineManager`` code to relate transactions and flows. This 
relationship is exposed in the eventing interface to the RPC clients, 
thus allowing them to track the end result of a flow and map to the 
actual transactions/states completed. Otherwise this service is unlikely 
to be accessed by any CorDapps. The 
``InMemoryStateMachineRecordedTransactionMappingStorage`` service is 
available as a non-persistent implementation for unit tests with no database. 

DBTransactionStorage
~~~~~~~~~~~~~~~~~~~~

The ``DBTransactionStorage`` service is a persistent implementation of 
the ``TransactionStorage`` interface and allows flows read-only 
access to full transactions, plus transaction level event callbacks. 
Storage of new transactions must be made via the ``recordTransactions`` 
method on the ``ServiceHub``, not via a direct call to this service, so 
that the various event notifications can occur. 

NodeAttachmentService
~~~~~~~~~~~~~~~~~~~~~

The ``NodeAttachmentService`` provides an implementation of the 
``AttachmentStorage`` interface exposed on the ``ServiceHub`` allowing 
transactions to add documents, copies of the contract code and binary 
data to transactions. The service is also interfaced to by the web server,
which allows files to be uploaded via an HTTP post request.

Flow framework and event scheduling services
--------------------------------------------

StateMachineManager
~~~~~~~~~~~~~~~~~~~

The ``StateMachineManager`` is the service that runs the active 
flows of the node whether initiated by an RPC client, the web 
interface, a scheduled state activity, or triggered by receipt of a 
message from another node. The ``StateMachineManager`` wraps the 
flow code (extensions of the ``FlowLogic`` class) inside an 
instance of the ``FlowStateMachineImpl`` class, which is a 
``Quasar`` ``Fiber``. This allows the ``StateMachineManager`` to suspend 
flows at all key lifecycle points and persist their serialized state 
to the database via the ``DBCheckpointStorage`` service. This process 
uses the facilities of the ``Quasar`` ``Fibers`` library to manage this 
process and hence the requirement for the node to run the ``Quasar`` 
java instrumentation agent in its JVM. 

In operation the ``StateMachineManager`` is typically running an active 
flow on its server thread until it encounters a blocking, or 
externally visible operation, such as sending a message, waiting for a 
message, or initiating a ``subFlow``. The fiber is then suspended 
and its stack frames serialized to the database, thus ensuring that if 
the node is stopped, or crashes at this point the flow will restart 
with exactly the same action again. To further ensure consistency, every 
event which resumes a flow opens a database transaction, which is 
committed during this suspension process ensuring that the database 
modifications e.g. state commits stay in sync with the mutating changes 
of the flow. Having recorded the fiber state the 
``StateMachineManager`` then carries out the network actions as required 
(internally one flow message exchanged may actually involve several 
physical session messages to authenticate and invoke registered 
flows on the remote nodes). The flow will stay suspended until 
the required message is returned and the scheduler will resume 
processing of other activated flows. On receipt of the expected 
response message from the network layer the ``StateMachineManager`` 
locates the appropriate flow, resuming it immediately after the 
blocking step with the received message. Thus from the perspective of 
the flow the code executes as a simple linear progression of 
processing, even if there were node restarts and possibly message 
resends (the messaging layer deduplicates messages based on an id that 
is part of the checkpoint). 

The ``StateMachineManager`` service is not directly exposed to the 
flows, or contracts themselves. 

NodeSchedulerService
~~~~~~~~~~~~~~~~~~~~

The ``NodeSchedulerService`` implements the ``SchedulerService`` 
interface and monitors the Vault updates to track any new states that 
implement the ``SchedulableState`` interface and require automatic 
scheduled flow initiation. At the scheduled due time the 
``NodeSchedulerService`` will create a new flow instance passing it 
a reference to the state that triggered the event. The flow can then 
begin whatever action is required. Note that the scheduled activity 
occurs in all nodes holding the state in their Vault, it may therefore 
be required for the flow to exit early if the current node is not 
the intended initiator.

Vault related services
----------------------

NodeVaultService
~~~~~~~~~~~~~~~~

The ``NodeVaultService`` implements the ``VaultService`` interface to 
allow access to the node's own set of unconsumed states. The service 
does this by tracking update notifications from the 
``TransactionStorage`` service and processing relevant updates to delete 
consumed states and insert new states. The resulting update is then 
persisted to the database. The ``VaultService`` then exposes query and 
event notification APIs to flows and CorDapp services to allow them
to respond to updates, or query for states meeting various conditions to 
begin the formation of new transactions consuming them. The equivalent 
services are also forwarded to RPC clients, so that they may show 
updating views of states held by the node. 

NodeSchemaService and HibernateObserver
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The ``HibernateObserver`` runs within the node framework and listens for 
vault state updates, the ``HibernateObserver`` then uses the mapping 
services of the ``NodeSchemaService`` to record the states in auxiliary 
database tables. This allows Corda state updates to be exposed to 
external legacy systems by insertion of unpacked data into existing 
tables. To enable these features the contract state must implement the 
``QueryableState`` interface to define the mappings. 

Corda Web Server
----------------

A simple web server is provided that embeds the Jetty servlet container.
The Corda web server is not meant to be used for real, production-quality
web apps. Instead it shows one example way of using Corda RPC in web apps
to provide a REST API on top of the Corda native RPC mechanism.

.. note:: The Corda web server may be removed in future and replaced with
   sample specific webapps using a standard framework like Spring Boot.