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

In addition to the evolution of the platform, flows that run on top of the platform can also evolve. It may be that the
flow protocol between an initiating flow and its initiated flow changes from one CorDapp release to the next in such a
way to be backward incompatible with existing flows. For example, if a sequence of sends and receives needs to change
or if the semantics of a particular receive changes.

The ``InitiatingFlow`` annotation (see :doc:`flow-state-machine` for more information on the flow annotations) has a ``version``
property, which if not specified defaults to 1. This flow version is included in the flow session handshake and exposed
to both parties in the communication via ``FlowLogic.getFlowContext``. This takes in a ``Party`` and will return a
``FlowContext`` object which describes the flow running on the other side. In particular it has the ``flowVersion`` property
which can be used to programmatically evolve flows across versions.

.. container:: codeset

   .. sourcecode:: kotlin

        @Suspendable
        override fun call() {
            val flowVersionOfOtherParty = getFlowContext(otherParty).flowVersion
            val receivedString = if (flowVersionOfOtherParty == 1) {
                receive<Int>(otherParty).unwrap { it.toString() }
            } else {
                receive<String>(otherParty).unwrap { it }
            }
        }

The above shows an example evolution of a flow which in the first version was expecting to receive an Int, but then
in subsequent versions was relaxed to receive a String. This flow is still able to communicate with parties which are
running the older flow (or rather older CorDapps containing the older flow).

.. warning:: It's important that ``InitiatingFlow.version`` be incremented each time the flow protocol changes in an
   incompatible way.

``FlowContext`` also has ``appName`` which is the name of the CorDapp hosting the flow. This can be used to determine
implementation details of the CorDapp. See :doc:`cordapp-build-systems` for more information on the CorDapp filename.

.. note:: Currently changing any of the properties of a ``CordaSerializable`` type is also backwards incompatible and
   requires incrementing of ``InitiatingFlow.version``. This will be relaxed somewhat once the AMQP wire serialisation
   format is implemented as it will automatically handle a lot of the data type migration cases.

