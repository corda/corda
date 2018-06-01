Network permissioning
=====================

.. contents::

Every Corda node is a part of a network (also called a zone), and networks are *permissioned*. To connect to a
zone, a node needs a signed X.509 certificate from the network operator. This takes the form of three keystores in a node's
``<workspace>/certificates/`` folder:

* ``network-root-truststore.jks``, which stores the network/zone operator's public keys and certificates
* ``nodekeystore.jks``, which stores the node’s identity keypairs and certificates
* ``sslkeystore.jks``, which stores the node’s TLS keypairs and certificates

Production deployments require a secure certificate authority. Most users will join an existing network such as the
main Corda network or the Corda TestNet. You can also build your own networks. During development, no particular
network is required because you can use the included tools to pre-create and pre-distribute the certificates and
map files that would normally be provided dynamically by the network.

Certificate hierarchy
---------------------

A Corda network has three types of certificate authorities (CAs):

* The **root network CA**, that defines the extent of a compatibility zone.
* The **doorman CA**. The doorman CA is used instead of the root network CA for day-to-day key signing to reduce the
  risk of the root network CA's private key being compromised. This is equivalent to an intermediate certificate
  in the web PKI.
* Each node also serves as its own CA in issuing the child certificates that it uses to sign its identity keys and TLS
  certificates.

Each certificate has an X.509 extension in it that defines the certificate/key's role in the system (see below for details).
They also use X.509 name constraints to ensure that the X.500 names that encode a human meaningful identity are propagated
to all the child certificates properly. The following constraints are imposed:

* Doorman certificates are issued by a network root. Network root certs do not contain a role extension.
* Node certificates are issued by an entity with a doorman certificate.
* Legal identity/TLS certificates are issued by a certificate marked as node CA.
* Confidential identity certificates are issued by a certificate marked as well known legal identity.
* Party certificates are marked as either a well known identity or a confidential identity.

The structure of certificates above Doorman/Network map is intentionally left untouched, as they are not relevant to
the identity service and therefore there is no advantage in enforcing a specific structure on those certificates. The
certificate hierarchy consistency checks are required because nodes can issue their own certificates and can set
their own role flags on certificates, and it's important to verify that these are set consistently with the
certificate hierarchy design. As as side-effect this also acts as a secondary depth restriction on issued
certificates.

We can visualise the permissioning structure as follows:

.. image:: resources/certificate_structure.png
   :scale: 55%
   :align: center

Keypair and certificate formats
-------------------------------

You can use any standard key tools to create the required public/private keypairs and certificates. The keypairs and
certificates must obey the following restrictions:

1. The certificates must follow the `X.509v3 standard <https://tools.ietf.org/html/rfc5280>`__
2. The TLS certificates must follow the `TLS v1.2 standard <https://tools.ietf.org/html/rfc5246>`__
3. The root network CA, doorman CA and node CA keys, as well as the node TLS keys, must follow one of the following schemes:
    * ECDSA using the NIST P-256 curve (secp256r1)
    * ECDSA using the Koblitz k1 curve (secp256k1)
    * RSA with 3072-bit key size or higher.

The certificates and keys will be automatically generated for you by the node on first run. However, you can also generate
them manually for more control. The ``X509Utilities`` class shows how to generate the required public/private keypairs
and certificates using Bouncy Castle.
You can find the ``X509Utilities`` in the `Corda repository <https://github.com/corda/corda>`__, under ``/node-api/src/main/kotlin/net/corda/nodeapi/internal/crypto/X509Utilities.kt``.

Certificate role extension
--------------------------

Corda certificates have a custom X.509v3 extension that specifies the role the certificate relates to. This extension
has the OID ``1.3.6.1.4.1.50530.1.1`` and is non-critical, so implementations outside of Corda nodes can safely ignore it.
The extension contains a single ASN.1 integer identifying the identity type the certificate is for:

1. Doorman
2. Network map
3. Service identity (currently only used as the shared identity in distributed notaries)
4. Node certificate authority (from which the TLS and well-known identity certificates are issued)
5. Transport layer security
6. Well-known legal identity
7. Confidential legal identity

In a typical installation, node administrators need not be aware of these. However, when node certificates are managed
by external tools (such as an existing PKI solution deployed within an organisation), it is important to understand
these constraints.

Certificate path validation is extended so that a certificate must contain the extension if the extension was present
in the certificate of the issuer.


Manually creating the node keys
-------------------------------

The node expects a Java-style key store (this may change in future to support PKCS#12 keystores) called ``nodekeystore.jks``,
with the private key and certificate having an alias of "cordaclientca". This certificate should be signed by the
doorman CA for your network. The basic constraints extension must be set to true.

For the TLS keys, the basic constraints extension must be set to false. The keystore name is ``sslkeystore.jks`` and
the key alias must be ``cordaclienttls``.

These two files should be in the node's certificate directory (``<workspace>/certificates/``), along with the network's
own root certificates in a ``network-root-truststore.jks`` file.

Connecting to a compatibility zone
----------------------------------

To connect to a compatibility zone you need to register with their certificate signing authority (doorman) by submitting
a certificate signing request (CSR) to obtain a valid identity for the zone. You could do this out of band, for instance
via email or a web form, but there's also a simple request/response protocol built into Corda.

Before you can register, you must first have received the trust store file containing the root certificate from the zone
operator. For high security zones this might be delivered physically. Then run the following command:

``java -jar corda.jar --initial-registration --network-root-truststore-password <trust store password>``

By default it will expect the trust store file to be in the location ``certificates/network-root-truststore.jks``.
This can be overridden with the additional ``--network-root-truststore`` flag.

The certificate signing request will be created based on node information obtained from the node configuration.
The following information from the node configuration file is needed to generate the request.

* **myLegalName** Your company's legal name as an X.500 string. X.500 allows differentiation between entities with the same
  name, as the legal name needs to be unique on the network. If another node has already been permissioned with this
  name then the permissioning server will automatically reject the request. The request will also be rejected if it
  violates legal name rules, see :ref:`node_naming` for more information. You can use the X.500 schema to disambiguate
  entities that have the same or similar brand names.

* **emailAddress** e.g. "admin@company.com"

* **devMode** must be set to false

* **networkServices or compatibilityZoneURL** The Corda compatibility zone services must be configured. This must be either:

  * **compatibilityZoneURL** The Corda compatibility zone network management service root URL.
  * **networkServices** Replaces the ``compatibilityZoneURL`` when the doorman and network map services
    are configured to operate on different URL endpoints. The ``doorman`` entry is used for registration.

A new pair of private and public keys generated by the Corda node will be used to create the request.

The utility will submit the request to the doorman server and poll for a result periodically to retrieve the certificates.
Once the request has been approved and the certificates downloaded from the server, the node will create the keystore and trust store using the certificates and the generated private key.

.. note:: You can exit the utility at any time if the approval process is taking longer than expected. The request process will resume on restart.

This process only is needed when the node connects to the network for the first time, or when the certificate expires.

Creating your own compatibility zone
------------------------------------

This section documents how to implement your own doorman and network map servers, which is the basic process required to
create a dedicated zone. At this time we do not provide tooling to do this, because the needs of each zone are different
and no generic, configurable doorman codebase has been written.

Do you need a zone?
^^^^^^^^^^^^^^^^^^^

Think twice before going this route:

1. It isn't necessary for testing.
2. It isn't necessary to add another layer of permissioning or 'know your customer' requirements onto your app.

**Testing.** Creating a production-ready zone isn't necessary for testing as you can use the *network bootstrapper*
tool to create all the certificates, keys and distribute map files needed to run many nodes. The bootstrapper can
create a network locally on your desktop/laptop but it also knows how to automate cloud providers via their APIs and
using Docker. In this way you can bring up a simulation of a real Corda network with different nodes on different
machines in the cloud for your own testing. Testing this way has several advantages, most obviously that you avoid
flakyness caused by synchronisation issues during network bringup. You can read more about the reasons for the
creation of the bootstrapper tool
`in a blog post on the design thinking behind Corda's network map infrastructure <https://medium.com/corda/cordas-new-network-map-infrastructure-8c4c248fd7f3>`__.

**Permissioning.** And creating a zone is also unnecessary for imposing permissioning requirements beyond that of the
base Corda network. You can control who can use your app by creating a *business network*. A business network is what we
call a coalition of nodes that have chosen to run a particular app within a given commercial context. Business networks
aren't represented in the Corda API at this time, partly because the technical side is so simple. You can create one
via a simple three step process:

1. Distribute a list of X.500 names that are members of your business network, e.g. a simple way to do this is by
   hosting a text file with one name per line on your website at a fixed HTTPS URL. You could also write a simple
   request/response flow that serves the list over the Corda protocol itself, although this requires the business
   network to have a node for itself.
2. Write a bit of code that downloads and caches the contents of this file on disk, and which loads it into memory in
   the node. A good place to do this is in a class annotated with ``@CordaService``, because this class can expose
   a ``Set<Party>`` field representing the membership of your service.
3. In your flows use ``serviceHub.findService`` to get a reference to your ``@CordaService`` class, read the list of
   members and at the start of each flow, throw a FlowException if the counterparty isn't in the membership list.

In this way you can impose a centrally controlled ACL that all members will collectively enforce.

Why create your own zone?
^^^^^^^^^^^^^^^^^^^^^^^^^

The primary reason to create a zone and provide the associated infrastructure is control over *network parameters*. These
are settings that control Corda's operation, and on which all users in a network must agree. Failure to agree would create
the Corda equivalent of a blockchain "hard fork". Parameters control things like how quickly users should upgrade,
how long nodes can be offline before they are evicted from the system and so on.

Creating a zone involves the following steps:

1. Create the zone private keys and certificates. This procedure is conventional and no special knowledge is required:
   any self-signed set of certificates can be used. A professional quality zone will probably keep the keys inside a
   hardware security module (as the main Corda network and test networks do).
2. Write a network map server.
3. Optionally, create a doorman server.
4. Finally, you would select and generate your network parameter file.

Writing a network map server
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

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
^^^^^^^^^^^^^^^^^^^^^^^^

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
^^^^^^^^^^^^^^^^^^^^^^^

Zone parameters are stored in a file containing a Corda AMQP serialised ``SignedDataWithCert<NetworkParameters>`` object.
It is easy to create such a file with a small Java or Kotlin program. The ``NetworkParameters`` object is a simple data
holder that could be read from e.g. a config file, or settings from a database.

Signing and saving the resulting file is just a few lines of code. A full example can be found in ``NetworkParametersCopier.kt`` in the
source tree, but a flavour of it looks like this:

.. container:: codeset
   .. sourcecode:: java

      NetworkParameters networkParameters = new NetworkParameters(
                4,                        // minPlatformVersion
                Collections.emptyList(),  // notaries
                1024 * 1024 * 20,         // maxMessageSize
                1024 * 1024 * 15,         // maxTransactionSize
                Instant.now(),            // modifiedTime
                2,                        // epoch
                Collections.emptyMap()    // whitelist
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
      val signedParams: SerializedBytes<SignedDataWithCert<NetworkParameters>> = signingCertAndKeyPair.sign(networkParameters).serialize()
      signedParams.open().copyTo(Paths.get("/some/path"))

Each individual parameter is documented in `the JavaDocs/KDocs for the NetworkParameters class <https://docs.corda.net/api/kotlin/corda/net.corda.core.node/-network-parameters/index.html>`__.

The network map certificate is usually chained off the root certificate, and can be created according to the instructions above.

Each time the zone parameters are changed, the epoch should be incremented. Epochs are essentially version numbers for
the parameters, and they therefore cannot go backwards.

Once saved, the new parameters can be served by the network map server.

Selecting parameter values
^^^^^^^^^^^^^^^^^^^^^^^^^^

How to choose the parameters? This is the most complex question facing you as a new zone operator. Some settings may seem
straightforward and others may involve cost/benefit tradeoffs specific to your business. For example, you could choose
to run a validating notary yourself, in which case you would (in the absence of SGX) see all the users data. Or you could
run a non-validating notary, with BFT fault tolerance, which implies recruiting others to take part in the cluster.

New network parameters will be added over time as Corda evolves. You will need to ensure that when your users upgrade,
all the new network parameters are being served. You can ask for advice on the `corda-dev mailing list <https://groups.io/g/corda-dev>`__.