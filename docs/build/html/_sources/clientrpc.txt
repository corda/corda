Client RPC
==========

There are multiple ways to interact with a node from a *client program*, but if your client is written in a JVM
compatible language the easiest way to do so is using the client library. The library connects to your running
node using a message queue protocol and then provides a simple RPC interface to interact with it. You make calls
on a Java object as normal, and the marshalling back and forth is handled for you.

The starting point for the client library is the `CordaRPCClient`_ class. This provides a ``proxy`` method that
returns an implementation of the `CordaRPCOps`_ interface. A timeout parameter can be specified, and observables that
are returned by RPCs can be subscribed to in order to receive an ongoing stream of updates from the node. More
detail on how to use this is provided in the docs for the proxy method.

.. warning:: The returned object is somewhat expensive to create and consumes a small amount of server side
   resources. When you're done with it, cast it to ``Closeable`` or ``AutoCloseable`` and close it. Don't create
   one for every call you make - create a proxy and reuse it.

For a brief tutorial on how one can use the RPC API see :doc:`tutorial-clientrpc-api`.

Security
--------

Users wanting to use the RPC library are first required to authenticate themselves with the node using a valid username
and password. These are specified in the configuration file. Each user can be configured with a set of permissions which
the RPC can use for fine-grain access control.

Observables
-----------

The RPC system handles observables in a special way. When a method returns an observable, whether directly or
as a sub-object of the response object graph, an observable is created on the client to match the one on the
server. Objects emitted by the server-side observable are pushed onto a queue which is then drained by the client.
The returned observable may even emit object graphs with even more observables in them, and it all works as you
would expect.

This feature comes with a cost: the server must queue up objects emitted by the server-side observable until you
download them. Therefore RPCs that use this feature are marked with the ``@RPCReturnsObservables`` annotation, and
you are expected to subscribe to all the observables returned. If you don't want an observable then subscribe
then unsubscribe immediately to clear the buffers and indicate that you aren't interested. If your app quits then
server side resources will be freed automatically.

When all the observables returned by an RPC are unsubscribed on the client side, that unsubscription propagates
through to the server where the corresponding server-side observables are also unsubscribed.

.. warning:: If you leak an observable or proxy on the client side and it gets garbage collected, you will get
   a warning printed to the logs and the proxy will be closed for you. But don't rely on this, as garbage
   collection is non-deterministic.

Futures
-------

A method can also return a ``ListenableFuture`` in its object graph and it will be treated in a similar manner to
observables, including needing to mark the RPC with the ``@RPCReturnsObservables`` annotation. Unlike for an observable,
once the single value (or an exception) has been received all server-side resources will be released automatically. Calling
the ``cancel`` method on the future will unsubscribe it from any future value and release any resources.

Versioning
----------

The client RPC protocol is versioned with a simple incrementing integer. When a proxy is created the server is
queried for its protocol version, and you can specify your minimum requirement. Methods added in later versions
are tagged with the ``@RPCSinceVersion`` annotation. If you try to use a method that the server isn't advertising
support of, an ``UnsupportedOperationException`` is thrown. If you want to know the version of the server, just
use the ``protocolVersion`` property (i.e. ``getProtocolVersion`` in Java).

Thread safety
-------------

A proxy is thread safe, blocking, and will only allow a single RPC to be in flight at once. Any observables that
are returned and you subscribe to will have objects emitted on a background thread. Observables returned as part
of one RPC and observables returned from another may have their callbacks invoked in parallel, but observables
returned as part of the same specific RPC invocation are processed serially and will not be invoked in parallel.

If you want to make multiple calls to the server in parallel you can do that by creating multiple proxies, but
be aware that the server itself may *not* process your work in parallel even if you make your requests that way.

Error handling
--------------

If something goes wrong with the RPC infrastructure itself, an ``RPCException`` is thrown. If you call a method that
requires a higher version of the protocol than the server supports, ``UnsupportedOperationException`` is thrown.
Otherwise, if the server implementation throws an exception, that exception is serialised and rethrown on the client
side as if it was thrown from inside the called RPC method. These exceptions can be caught as normal.

Wire protocol
-------------

The client RPC wire protocol is not currently documented. To use it you must use the client library provided.
This is likely to change in a future release.

Registering classes with RPC Kryo
---------------------------------

In the present implementation of the node we use Kryo to generate the *on the wire* representation of contracts states
or any other classes that form part of the RPC arguments or response.  To avoid the RPC interface being wide open to all
classes on the classpath, Cordapps will currently have to register any classes or custom serialisers they require with Kryo
if they are not one of those registered by default in ``RPCKryo`` via the plugin architecture.  See :doc:`creating-a-cordapp`.
This will require some familiarity with Kryo.  An example is shown in :doc:`tutorial-clientrpc-api`.

.. warning:: We will be replacing the use of Kryo in RPC with a stable message format and this will mean that this plugin
   customisation point will either go away completely or change.

.. _CordaRPCClient: api/kotlin/corda/net.corda.client/-corda-r-p-c-client/index.html
.. _CordaRPCOps: api/kotlin/corda/net.corda.node.services.messaging/-corda-r-p-c-ops/index.html
