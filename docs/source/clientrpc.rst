.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Interacting with a node
=======================

.. contents::

Overview
--------
To interact with your node, you need to write a client in a JVM-compatible language using the `CordaRPCClient`_ class.
This class allows you to connect to your node via a message queue protocol and provides a simple RPC interface for
interacting with the node. You make calls on a JVM object as normal, and the marshalling back-and-forth is handled for
you.

.. warning:: The built-in Corda webserver is deprecated and unsuitable for production use. If you want to interact with
   your node via HTTP, you will need to stand up your own webserver that connects to your node using the
   `CordaRPCClient`_ class. You can find an example of how to do this using the popular Spring Boot server
   `here <https://github.com/corda/spring-webserver>`_.

.. _clientrpc_connect_ref:

Connecting to a node via RPC
----------------------------
To use `CordaRPCClient`_, you must add ``net.corda:corda-rpc:$corda_release_version`` as a ``cordaCompile`` dependency
in your client's ``build.gradle`` file.

`CordaRPCClient`_ has a ``start`` method that takes the node's RPC address and returns a `CordaRPCConnection`_.
`CordaRPCConnection`_ has a ``proxy`` method that takes an RPC username and password and returns a `CordaRPCOps`_
object that you can use to interact with the node.

Here is an example of using `CordaRPCClient`_ to connect to a node and log the current time on its internal clock:

.. container:: codeset

   .. literalinclude:: example-code/src/main/kotlin/net/corda/docs/kotlin/ClientRpcExample.kt
      :language: kotlin
      :start-after: START 1
      :end-before: END 1

   .. literalinclude:: example-code/src/main/java/net/corda/docs/java/ClientRpcExample.java
      :language: java
      :start-after: START 1
      :end-before: END 1

.. warning:: The returned `CordaRPCConnection`_ is somewhat expensive to create and consumes a small amount of
   server side resources. When you're done with it, call ``close`` on it. Alternatively you may use the ``use``
   method on `CordaRPCClient`_ which cleans up automatically after the passed in lambda finishes. Don't create
   a new proxy for every call you make - reuse an existing one.

For further information on using the RPC API, see :doc:`tutorial-clientrpc-api`.

RPC permissions
---------------
For a node's owner to interact with their node via RPC, they must define one or more RPC users. Each user is
authenticated with a username and password, and is assigned a set of permissions that control which RPC operations they
can perform. Permissions are not required to interact with the node via the shell, unless the shell is being accessed via SSH.

RPC users are created by adding them to the ``rpcUsers`` list in the node's ``node.conf`` file:

.. container:: codeset

    .. sourcecode:: groovy

        rpcUsers=[
            {
                username=exampleUser
                password=examplePass
                permissions=[]
            },
            ...
        ]

By default, RPC users are not permissioned to perform any RPC operations.

Granting flow permissions
~~~~~~~~~~~~~~~~~~~~~~~~~
You provide an RPC user with the permission to start a specific flow using the syntax
``StartFlow.<fully qualified flow name>``:

.. container:: codeset

    .. sourcecode:: groovy

        rpcUsers=[
            {
                username=exampleUser
                password=examplePass
                permissions=[
                    "StartFlow.net.corda.flows.ExampleFlow1",
                    "StartFlow.net.corda.flows.ExampleFlow2"
                ]
            },
            ...
        ]

You can also provide an RPC user with the permission to start any flow using the syntax
``InvokeRpc.startFlow``:

.. container:: codeset

    .. sourcecode:: groovy

        rpcUsers=[
            {
                username=exampleUser
                password=examplePass
                permissions=[
                    "InvokeRpc.startFlow"
                ]
            },
            ...
        ]

Granting other RPC permissions
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
You provide an RPC user with the permission to perform a specific RPC operation using the syntax
``InvokeRpc.<rpc method name>``:

.. container:: codeset

    .. sourcecode:: groovy

        rpcUsers=[
            {
                username=exampleUser
                password=examplePass
                permissions=[
                    "InvokeRpc.nodeInfo",
                    "InvokeRpc.networkMapSnapshot"
                ]
            },
            ...
        ]

Granting all permissions
~~~~~~~~~~~~~~~~~~~~~~~~
You can provide an RPC user with the permission to perform any RPC operation (including starting any flow) using the
``ALL`` permission:

.. container:: codeset

    .. sourcecode:: groovy

        rpcUsers=[
            {
                username=exampleUser
                password=examplePass
                permissions=[
                    "ALL"
                ]
            },
            ...
        ]

.. _rpc_security_mgmt_ref:

RPC security management
-----------------------

Setting ``rpcUsers`` provides a simple way of granting RPC permissions to a fixed set of users, but has some
obvious shortcomings. To support use cases aiming for higher security and flexibility, Corda offers additional security
features such as:

 * Fetching users credentials and permissions from an external data source (e.g.: a remote RDBMS), with optional in-memory
   caching. In particular, this allows credentials and permissions to be updated externally without requiring nodes to be
   restarted.
 * Password stored in hash-encrypted form. This is regarded as must-have when security is a concern. Corda currently supports
   a flexible password hash format conforming to the Modular Crypt Format provided by the `Apache Shiro framework <https://shiro.apache.org/static/1.2.5/apidocs/org/apache/shiro/crypto/hash/format/Shiro1CryptFormat.html>`_

These features are controlled by a set of options nested in the ``security`` field of ``node.conf``.
The following example shows how to configure retrieval of users credentials and permissions from a remote database with
passwords in hash-encrypted format and enable in-memory caching of users data:

.. container:: codeset

    .. sourcecode:: groovy

        security = {
            authService = {
                dataSource = {
                    type = "DB"
                    passwordEncryption = "SHIRO_1_CRYPT"
                    connection = {
                       jdbcUrl = "<jdbc connection string>"
                       username = "<db username>"
                       password = "<db user password>"
                       driverClassName = "<JDBC driver>"
                    }
                }
                options = {
                     cache = {
                        expireAfterSecs = 120
                        maxEntries = 10000
                     }
                }
            }
        }

It is also possible to have a static list of users embedded in the ``security`` structure by specifying a ``dataSource``
of ``INMEMORY`` type:

.. container:: codeset

    .. sourcecode:: groovy

        security = {
            authService = {
                dataSource = {
                    type = "INMEMORY"
                    users = [
                        {
                            username = "<username>"
                            password = "<password>"
                            permissions = ["<permission 1>", "<permission 2>", ...]
                        },
                        ...
                    ]
                }
            }
        }

.. warning:: A valid configuration cannot specify both the ``rpcUsers`` and ``security`` fields. Doing so will trigger
   an exception at node startup.

Authentication/authorisation data
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The ``dataSource`` structure defines the data provider supplying credentials and permissions for users. There exist two
supported types of such data source, identified by the ``dataSource.type`` field:

 :INMEMORY: A static list of user credentials and permissions specified by the ``users`` field.

 :DB: An external RDBMS accessed via the JDBC connection described by ``connection``. Note that, unlike the ``INMEMORY``
  case, in a user database permissions are assigned to *roles* rather than individual users. The current implementation
  expects the database to store data according to the following schema:

       - Table ``users`` containing columns ``username`` and ``password``. The ``username`` column *must have unique values*.
       - Table ``user_roles`` containing columns ``username`` and ``role_name`` associating a user to a set of *roles*.
       - Table ``roles_permissions`` containing columns ``role_name`` and ``permission`` associating a role to a set of
         permission strings.

  .. note:: There is no prescription on the SQL type of each column (although our tests were conducted on ``username`` and
    ``role_name`` declared of SQL type ``VARCHAR`` and ``password`` of ``TEXT`` type). It is also possible to have extra columns
    in each table alongside the expected ones.

Password encryption
~~~~~~~~~~~~~~~~~~~

Storing passwords in plain text is discouraged in applications where security is critical. Passwords are assumed
to be in plain format by default, unless a different format is specified by the ``passwordEncryption`` field, like:

.. container:: codeset

    .. sourcecode:: groovy

        passwordEncryption = SHIRO_1_CRYPT

``SHIRO_1_CRYPT`` identifies the `Apache Shiro fully reversible
Modular Crypt Format <https://shiro.apache.org/static/1.2.5/apidocs/org/apache/shiro/crypto/hash/format/Shiro1CryptFormat.html>`_,
it is currently the only non-plain password hash-encryption format supported. Hash-encrypted passwords in this
format can be produced by using the `Apache Shiro Hasher command line tool <https://shiro.apache.org/command-line-hasher.html>`_.

Caching user accounts data
~~~~~~~~~~~~~~~~~~~~~~~~~~

A cache layer on top of the external data source of users credentials and permissions can significantly improve
performances in some cases, with the disadvantage of causing a (controllable) delay in picking up updates to the underlying data.
Caching is disabled by default, it can be enabled by defining the ``options.cache`` field in ``security.authService``,
for example:

.. container:: codeset

    .. sourcecode:: groovy

        options = {
             cache = {
                expireAfterSecs = 120
                maxEntries = 10000
             }
        }

This will enable a non-persistent cache contained in the node's memory with maximum number of entries set to ``maxEntries``
where entries are expired and refreshed after ``expireAfterSecs`` seconds.

Observables
-----------
The RPC system handles observables in a special way. When a method returns an observable, whether directly or
as a sub-object of the response object graph, an observable is created on the client to match the one on the
server. Objects emitted by the server-side observable are pushed onto a queue which is then drained by the client.
The returned observable may even emit object graphs with even more observables in them, and it all works as you
would expect.

This feature comes with a cost: the server must queue up objects emitted by the server-side observable until you
download them. Note that the server side observation buffer is bounded, once it fills up the client is considered
slow and will be disconnected. You are expected to subscribe to all the observables returned, otherwise client-side
memory starts filling up as observations come in. If you don't want an observable then subscribe then unsubscribe
immediately to clear the client-side buffers and to stop the server from streaming. For Kotlin users there is a
convenience extension method called ``notUsed()`` which can be called on an observable to automate this step.

If your app quits then server side resources will be freed automatically.

.. warning:: If you leak an observable on the client side and it gets garbage collected, you will get a warning
   printed to the logs and the observable will be unsubscribed for you. But don't rely on this, as garbage collection
   is non-deterministic. If you set ``-Dnet.corda.client.rpc.trackRpcCallSites=true`` on the JVM command line then
   this warning comes with a stack trace showing where the RPC that returned the forgotten observable was called from.
   This feature is off by default because tracking RPC call sites is moderately slow.

.. note:: Observables can only be used as return arguments of an RPC call. It is not currently possible to pass
   Observables as parameters to the RPC methods. In other words the streaming is always server to client and not
   the other way around.

Futures
-------
A method can also return a ``CordaFuture`` in its object graph and it will be treated in a similar manner to
observables. Calling the ``cancel`` method on the future will unsubscribe it from any future value and release
any resources.

Versioning
----------
The client RPC protocol is versioned using the node's platform version number (see :doc:`versioning`). When a proxy is created
the server is queried for its version, and you can specify your minimum requirement. Methods added in later versions
are tagged with the ``@RPCSinceVersion`` annotation. If you try to use a method that the server isn't advertising support
of, an ``UnsupportedOperationException`` is thrown. If you want to know the version of the server, just use the
``protocolVersion`` property (i.e. ``getProtocolVersion`` in Java).

The RPC client library defaults to requiring the platform version it was built with. That means if you use the client
library released as part of Corda N, then the node it connects to must be of version N or above. This is checked when
the client first connects. If you want to override this behaviour, you can alter the ``minimumServerProtocolVersion``
field in the ``CordaRPCClientConfiguration`` object passed to the client. Alternatively, just link your app against
an older version of the library.

Thread safety
-------------
A proxy is thread safe, blocking, and allows multiple RPCs to be in flight at once. Any observables that are returned and
you subscribe to will have objects emitted in order on a background thread pool. Each Observable stream is tied to a single
thread, however note that two separate Observables may invoke their respective callbacks on different threads.

Error handling
--------------
If something goes wrong with the RPC infrastructure itself, an ``RPCException`` is thrown. If you call a method that
requires a higher version of the protocol than the server supports, ``UnsupportedOperationException`` is thrown.
Otherwise the behaviour depends on the ``devMode`` node configuration option.

In ``devMode``, if the server implementation throws an exception, that exception is serialised and rethrown on the client
side as if it was thrown from inside the called RPC method. These exceptions can be caught as normal.

When not in ``devMode``, the server will mask exceptions not meant for clients and return an ``InternalNodeException`` instead.
This does not expose internal information to clients, strengthening privacy and security. CorDapps can have exceptions implement
``ClientRelevantError`` to allow them to reach RPC clients.

Reconnecting RPC clients
------------------------

In the current version of Corda, an RPC client connected to a node stops functioning when the node becomes unavailable or the associated TCP connection is interrupted.
Running RPC commands against a stopped node will just throw exceptions. Any subscriptions to ``Observable``\s that have been created before the disconnection will stop receiving events after the node restarts.
RPCs which have a side effect, such as starting flows, may or may not have executed on the node depending on when the client was disconnected.

It is the client's responsibility to handle these errors and reconnect once the node is running again. The client will have to re-subscribe to any ``Observable``\s in order to keep receiving updates.
With regards to RPCs with side effects, the client will have to inspect the state of the node to infer whether the flow was executed or not before retrying it.

Clients can make use of the options described below in order to take advantage of some automatic reconnection functionality that mitigates some of these issues.

Enabling automatic reconnection
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

If you provide a list of addresses via the ``haAddressPool`` argument when instantiating a ``CordaRPCClient``, then automatic reconnection will be performed when the existing connection is dropped.
However, any in-flight calls during reconnection will fail and previously returned observables will call ``onError``. The client code is responsible for waiting for the connection to be established
in order to retry any calls, retrieve new observables and re-subscribe to them.

Enabling graceful reconnection
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

A more graceful form of reconnection is also available, which will block all in-flight calls until the connection is re-established and
will also reconnect the existing ``Observable``\s, so that they keep emitting events to the existing subscribers.

.. warning:: In this approach, some events might be lost during a re-connection and not sent from the subscribed ``Observable``\s.

You can enable this graceful form of reconnection by using the ``gracefulReconnect`` parameter in the following way:

.. sourcecode:: kotlin

   val cordaClient = CordaRPCClient(nodeRpcAddress)
   val cordaRpcOps = cordaClient.start(rpcUserName, rpcUserPassword, gracefulReconnect = true).proxy

Logical  retries for flow invocation
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

As described above, if you want to retry a flow that failed during a disconnection, you will first need to verify it has not been previously executed.
The only way currently to confirm this is by performing a business-level query.

.. note:: Future releases of Corda are expected to contain new APIs for coping with reconnection in a more resilient way providing stricter
   safety guarantees.


Wire security
-------------
If TLS communications to the RPC endpoint are required the node should be configured with ``rpcSettings.useSSL=true`` see :doc:`corda-configuration-file`.
The node admin should then create a node specific RPC certificate and key, by running the node once with ``generate-rpc-ssl-settings`` command specified (see :doc:`node-commandline`).
The generated RPC TLS trust root certificate will be exported to a ``certificates/export/rpcssltruststore.jks`` file which should be distributed to the authorised RPC clients.

The connecting ``CordaRPCClient`` code must then use one of the constructors with a parameter of type ``ClientRpcSslOptions`` (`JavaDoc <api/javadoc/net/corda/client/rpc/CordaRPCClient.html>`_) and set this constructor
argument with the appropriate path for the ``rpcssltruststore.jks`` file. The client connection will then use this to validate the RPC server handshake.

Note that RPC TLS does not use mutual authentication, and delegates fine grained user authentication and authorisation to the RPC security features detailed above.

Whitelisting classes with the Corda node
----------------------------------------
CorDapps must whitelist any classes used over RPC with Corda's serialization framework, unless they are whitelisted by
default in ``DefaultWhitelist``. The whitelisting is done either via the plugin architecture or by using the
``@CordaSerializable`` annotation.  See :doc:`serialization`. An example is shown in :doc:`tutorial-clientrpc-api`.

.. _CordaRPCClient: api/javadoc/net/corda/client/rpc/CordaRPCClient.html
.. _CordaRPCOps: api/javadoc/net/corda/core/messaging/CordaRPCOps.html
.. _CordaRPCConnection: api/javadoc/net/corda/client/rpc/CordaRPCConnection.html
