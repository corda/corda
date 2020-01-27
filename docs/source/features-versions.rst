Corda Features to Versions
==========================

New versions of Corda introduce new features. These fall into one of three categories which have subtle but important implications for
node owners, application developers and network operators.

The first set are changes that have no impact on application developers or the Corda network protocol. An example would be support for
a new HSM or database system, for example, and which are of interest only to a node's operator.

The second set are new or changed APIs, which are of interest to CorDapp developers. When a release of Corda ships such features, the
Platform Version of that node is incremented so that a CorDapp that relies on such a new or changed feature can detect this (eg to
prevent it from running on a node without the feature or to trigger an alternative optimised codepath if the feature is present). The
app developer should set the CorDapp's minimumPlatformVersion parameter to signal the minimum Platform Version against which the app
can run or has been tested. If the application has also been tested against a greater platform version and can exploit it if present,
the node can also set the targetPlatformVersion field.

The third set of changes are those which could affect the operation of a Corda network. Examples would include a change to the
serialisation format or flow/wire protocol, or introduction of a new transaction component.  These are changes to the core data model and
these features have the property that it is not safe for any node or application to take advantage of until all nodes on the network
are capable of understanding them. Such features are thus only enabled in a node if the network to which it is connected has published
a minimumPlatformVersion in its network parameters that is greater than or equal to the Corda Platform Version that introduced the
feature. For example, Corda 4.0 nodes, which implement Corda Platform Version 4, can only take advantage of the Corda Reference States
feature when connected to a network with mPV 4.

If there is a Platform Version below which your application will not run or is not supported, then signal that with the application's
`CorDapp.mPV` field. This will prevent older nodes from running your app. Nodes which support newer Platform Versions may also use this
field to trigger code paths that emulate behaviours that were in force on that older Platform Version to maximise compatibility. However,
if you have tested your app against newer versions of Corda and know your node can take advantage of the new Platform Version behaviours
if present, you can signal this by using `CorDapp.targetPV` to declare the latest Platform Version against which the app has been tested
and is known to work. In this way, it is possible to ship CorDapps that can both run on all nodes supporting some minimum Platform Version
of Corda as well as opt in to newer features should they happen to be available on any given node.

.. list-table:: Corda Features
    :header-rows: 1

    * - Feature
      - Corda Platform Version (PV)
      - Min Network Platform Version (network mPV)
      - Introduced in OS version
      - Introduced in Enterprise version
    * - Observer Nodes
      - 2
      - 2
      - 2.0
      - n/a
    * - Corda Serialization Framework
      - 3
      - 3
      - 3.0
      - 3.0
    * - Hash Constraints
      - 1
      - 1
      - 1.0
      - 1.0
    * - Whitelist Constraints
      - 3
      - 3
      - 3.0
      - 3.0
    * - Inline Finality Flow
      - 4
      - 3
      - 4.0
      - 4.0
    * - Reference States
      - 4
      - 4
      - 4.0
      - 4.0
    * - Signature Constraints
      - 4
      - 4
      - 4.0
      - 4.0
    * - Underlying Support for Accounts
      - 5
      - 4
      - 4.3
      - 4.3
