Versioning
==========

As the Corda platform evolves and new features are added it becomes important to have a versioning system which allows
its users to easily compare versions and know what feature are available to them. Each Corda release uses the standard
semantic versioning scheme of ``major.minor.patch``. This is useful when making releases in the public domain but is not
friendly for a developer working on the platform. It first has to be parsed and then they have three separate segments on
which to determine API differences. The release version is still useful and every MQ message the node sends attaches it
to the ``release-version`` header property for debugging purposes.

Platform Version
----------------

It is much easier to use a single incrementing integer value to represent the API version of the Corda platform, which
is called the Platform Version. It is similar to Android's `API Level <https://developer.android.com/guide/topics/manifest/uses-sdk-element.html>`_.
It starts at 1 and will increment by exactly 1 for each release which changes any of the publicly exposed APIs in the
entire platform. This includes public APIs on the node itself, the RPC system, messaging, serialisation, etc. API backwards
compatibility will always be maintained, with the use of deprecation to migrate away from old APIs. In rare situations
APIs may have to be removed, for example due to security issues. There is no relationship between the Platform Version
and the release version - a change in the major, minor or patch values may or may not increase the Platform Version.

The Platform Version is part of the node's ``NodeInfo`` object, which is available from the ``ServiceHub``. This enables
a CorDapp to find out which version it's running on and determine whether a desired feature is available. When a node
registers with the Network Map Service it will use the node's Platform Version to enforce a minimum version requirement
for the network.

.. note:: A future release may introduce the concept of a target platform version, which would be similar to Android's
   ``targetSdkVersion``, and would provide a means of maintaining behavioural compatibility for the cases where the
   platform's behaviour has changed.

Flow versioning
---------------

A platform which can be extended with CorDapps also requires the ability to version these apps as they evolve from
release to release. This allows users of these apps, whether they're other nodes or RPC users, to select which version
they wish to use and enables nodes to control which app versions they support. Flows have their own version numbers,
independent of other versioning, for example of the platform. In particular it is the initiating flow that can be versioned
using the ``version`` property of the ``InitiatingFlow`` annotation. This assigns an integer version number, similar in
concept to the platform version, which is used in the session handshake process when a flow communicates with another party
for the first time. The other party will only accept the session request if it, firstly, has that flow loaded, and secondly,
for the same version (see also :doc:`flow-state-machine`).

.. note:: Currently we don't support multiple versions of the same flow loaded in the same node. This will be possible
   once we start loading CorDapps in separate class loaders.
