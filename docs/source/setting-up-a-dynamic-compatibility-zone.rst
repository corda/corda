.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Setting up a dynamic compatibility zone
=======================================

.. contents::

Do you need to create your own dynamic compatibility zone?
----------------------------------------------------------

By *dynamic compatibility zone*, we mean a compatibility zone that relies on a network map server to allow nodes to
join dynamically, instead of requiring each node to be bootstrapped and have the node-infos distributed manually. While
this may sound appealing, think twice before going down this route:

1. If you need to test a CorDapp, it is easier to create a test network using the network bootstrapper tool (see below)
2. If you need to control who uses your CorDapp, it is easier to apply permissioning by creating a business network
   (see below)

**Testing.** Creating a production-ready zone isn't necessary for testing as you can use the *network bootstrapper*
tool to create all the certificates, keys, and distribute the needed map files to run many nodes. The bootstrapper can
create a network locally on your desktop/laptop but it also knows how to automate cloud providers via their APIs and
using Docker. In this way you can bring up a simulation of a real Corda network with different nodes on different
machines in the cloud for your own testing. Testing this way has several advantages, most obviously that you avoid
race conditions in your tests caused by nodes/tests starting before all map data has propagated to all nodes.
You can read more about the reasons for the creation of the bootstrapper tool
`in a blog post on the design thinking behind Corda's network map infrastructure <https://medium.com/corda/cordas-new-network-map-infrastructure-8c4c248fd7f3>`__.

**Permissioning.** And creating a zone is also unnecessary for imposing permissioning requirements beyond that of the
base Corda network. You can control who can use your app by creating a *business network*. A business network is what we
call a coalition of nodes that have chosen to run a particular app within a given commercial context. Business networks
aren't represented in the Corda API at this time, partly because the technical side is so simple. You can create one
via a simple three step process:

1. Distribute a list of X.500 names that are members of your business network. You can use the
   `reference Business Network Membership Service implementation <https://github.com/corda/corda-solutions/tree/master/bn-apps/memberships-management>`_.
   Alternatively, you could do this is by hosting a text file with one name per line on your website at a fixed HTTPS
   URL. You could also write a simple request/response flow that serves the list over the Corda protocol itself,
   although this requires the business network to have its own node.
2. Write a bit of code that downloads and caches the contents of this file on disk, and which loads it into memory in
   the node. A good place to do this is in a class annotated with ``@CordaService``, because this class can expose
   a ``Set<Party>`` field representing the membership of your service.
3. In your flows use ``serviceHub.findService`` to get a reference to your ``@CordaService`` class, read the list of
   members and at the start of each flow, throw a FlowException if the counterparty isn't in the membership list.

In this way you can impose a centrally controlled ACL that all members will collectively enforce.

.. note:: A production-ready Corda network and a new iteration of the testnet will be available soon.

Why create your own zone?
-------------------------

The primary reason to create a zone and provide the associated infrastructure is control over *network parameters*. These
are settings that control Corda's operation, and on which all users in a network must agree. Failure to agree would create
the Corda equivalent of a blockchain "hard fork". Parameters control things like the root of identity,
how quickly users should upgrade, how long nodes can be offline before they are evicted from the system and so on.

Creating a zone involves the following steps:

1. Create the zone private keys and certificates. This procedure is conventional and no special knowledge is required:
   any self-signed set of certificates can be used. A professional quality zone will probably keep the keys inside a
   hardware security module (as the main Corda network and test networks do).
2. Write a network map server.
3. Optionally, create a doorman server.
4. Finally, you would select and generate your network parameter file.

How to create your own compatibility zone
-----------------------------------------

Using an existing network map implementation
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

You can use an existing network map implementation such as the
`Cordite Network Map Service <https://gitlab.com/cordite/network-map-service>`_ to create a dynamic compatibility zone.

Creating your own network map implementation
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Writing a network map server
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

This server implements a simple HTTP based protocol described in the ":doc:`network-map`" page.
The map server is responsible for gathering NodeInfo files from nodes, storing them, and distributing them back to the
nodes in the zone. By doing this it is also responsible for choosing who is in and who is out: having a signed
identity certificate is not enough to be a part of a Corda zone, you also need to be listed in the network map.
It can be thought of as a DNS equivalent. If you want to de-list a user, you would do it here.

It is very likely that your map server won't be entirely standalone, but rather, integrated with whatever your master
user database is.

The network map server also distributes signed network parameter files and controls the rollout schedule for when they
become available for download and opt-in, and when they become enforced. This is again a policy decision you will
probably choose to place some simple UI or workflow tooling around, in particular to enforce restrictions on who can
edit the map or the parameters.

Writing a doorman server
~~~~~~~~~~~~~~~~~~~~~~~~

This step is optional because your users can obtain a signed certificate in many different ways. The doorman protocol
is again a very simple HTTP based approach in which a node creates keys and requests a certificate, polling until it
gets back what it expects. However, you could also integrate this process with the rest of your signup process. For example,
by building a tool that's integrated with your payment flow (if payment is required to take part in your zone at all).
Alternatively you may wish to distribute USB smartcard tokens that generate the private key on first use, as is typically
seen in national PKIs. There are many options.

If you do choose to make a doorman server, the bulk of the code you write will be workflow related. For instance,
related to keeping track of an applicant as they proceed through approval. You should also impose any naming policies
you have in the doorman process. If names are meant to match identities registered in government databases then that
should be enforced here, alternatively, if names can be self-selected or anonymous, you would only bother with a
deduplication check. Again it will likely be integrated with a master user database.

Corda does not currently provide a doorman or network map service out of the box, partly because when stripped of the
zone specific policy there isn't much to them: just a basic HTTP server that most programmers will have favourite
frameworks for anyway.

The protocol is:

* If $URL = ``https://some.server.com/some/path``
* Node submits a PKCS#10 certificate signing request using HTTP POST to ``$URL/certificate``. It will have a MIME
  type of ``application/octet-stream``. The ``Client-Version`` header is set to be "1.0".
* The server returns an opaque string that references this request (let's call it ``$requestid``, or an HTTP error if something went wrong.
* The returned request ID should be persisted to disk, to handle zones where approval may take a long time due to manual
  intervention being required.
* The node starts polling ``$URL/$requestid`` using HTTP GET. The poll interval can be controlled by the server returning
  a response with a ``Cache-Control`` header.
* If the request is answered with a ``200 OK`` response, the body is expected to be a zip file. Each file is expected to
  be a binary X.509 certificate, and the certs are expected to be in order.
* If the request is answered with a ``204 No Content`` response, the node will try again later.
* If the request is answered with a ``403 Not Authorized`` response, the node will treat that as request rejection and give up.
* Other response codes will cause the node to abort with an exception.

Setting zone parameters
~~~~~~~~~~~~~~~~~~~~~~~

Zone parameters are stored in a file containing a Corda AMQP serialised ``SignedDataWithCert<NetworkParameters>``
object. It is easy to create such a file with a small Java or Kotlin program. The ``NetworkParameters`` object is a
simple data holder that could be read from e.g. a config file, or settings from a database. Signing and saving the
resulting file is just a few lines of code. A full example can be found in ``NetworkParametersCopier.kt`` in the source
tree, but a flavour of it looks like this:

.. container:: codeset

   .. sourcecode:: java

      NetworkParameters networkParameters = new NetworkParameters(
                4,                        // minPlatformVersion
                Collections.emptyList(),  // the `NotaryInfo`s of all the network's notaries
                1024 * 1024 * 20,         // maxMessageSize
                1024 * 1024 * 15,         // maxTransactionSize
                Instant.now(),            // modifiedTime
                2,                        // epoch
                Collections.emptyMap()    // whitelisted contract code JARs
      );
      CertificateAndKeyPair signingCertAndKeyPair = loadNetworkMapCA();
      SerializedBytes<SignedDataWithCert<NetworkParameters>> bytes = SerializedBytes.from(netMapCA.sign(networkParameters));
      Files.copy(bytes.open(), Paths.get("params-file"));

   .. sourcecode:: kotlin

      val networkParameters = NetworkParameters(
         minimumPlatformVersion = 4,
         notaries = listOf(...),
         maxMessageSize = 1024 * 1024 * 20   // 20mb, for example.
         maxTransactionSize = 1024 * 1024 * 15,
         modifiedTime = Instant.now(),
         epoch = 2,
         ... etc ...
      )
      val signingCertAndKeyPair: CertificateAndKeyPair = loadNetworkMapCA()
      val signedParams: SerializedBytes<SignedNetworkParameters> = signingCertAndKeyPair.sign(networkParameters).serialize()
      signedParams.open().copyTo(Paths.get("/some/path"))

Each individual parameter is documented in `the JavaDocs/KDocs for the NetworkParameters class
<https://docs.corda.net/api/kotlin/corda/net.corda.core.node/-network-parameters/index.html>`__. The network map
certificate is usually chained off the root certificate, and can be created according to the instructions above. Each
time the zone parameters are changed, the epoch should be incremented. Epochs are essentially version numbers for the
parameters, and they therefore cannot go backwards. Once saved, the new parameters can be served by the network map server.

Selecting parameter values
^^^^^^^^^^^^^^^^^^^^^^^^^^

How to choose the parameters? This is the most complex question facing you as a new zone operator. Some settings may seem
straightforward and others may involve cost/benefit tradeoffs specific to your business. For example, you could choose
to run a validating notary yourself, in which case you would (in the absence of SGX) see all the users' data. Or you could
run a non-validating notary, with BFT fault tolerance, which implies recruiting others to take part in the cluster.

New network parameters will be added over time as Corda evolves. You will need to ensure that when your users upgrade,
all the new network parameters are being served. You can ask for advice on the `corda-dev mailing list <https://groups.io/g/corda-dev>`__.
