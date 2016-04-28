Networking and messaging
========================

Although the platform does not currently provide a network backend, some preliminary interfaces are defined along with
an in-memory implementation provided for use by unit tests and other exploratory code. An implementation based on Apache
Kafka is also being developed, which should be sufficient for real use cases to be implemented in the short run, even
though in the long run a fully peer to peer protocol will be required.

This article quickly explains the basic networking interfaces in the code.

Messaging vs networking
-----------------------

It is important to understand that the code expects any networking module to provide the following services:

- Persistent, reliable and secure delivery of complete messages. The module is expected to retry delivery if initial
  attempts fail.
- Ability to send messages both 1:1 and 1:many, where 'many' may mean the entire group of network users.

The details of how this is achieved are not exposed to the rest of the code.

Interfaces
----------

The most important interface is called ``MessagingService`` and is defined in the ``core/messaging/Messaging.kt`` file.
It declares an interface with the following operations:

- ``addMessageHandler(topic: String, executor: Executor, callback: (Message, MessageHandlerRegistration) -> Unit)``
- ``createMessage(topic: String, data: ByteArray): Message``
- ``send(message: Message, targetRecipients: MessageRecipients)``
- ``stop()``

along with a few misc others that are not important enough to discuss here.

A *topic* is simply a string that identifies the kind of message that is being sent. When a message is received, the
topic is compared exactly to the list of registered message handlers and if it matches, the callback is invoked.
Adding a handler returns a ``MessageHandlerRegistration`` object that can be used to remove the handler, and that
registration object is also passed to each invocation to simplify the case where a handler wishes to remove itself.

Some helper functions are also provided that simplify the process of sending a message by using Kryo serialisation, and
registering one-shot handlers that remove themselves once they finished running, but those don't need to be implemented
by network module authors themselves.

Destinations are represented using opaque classes (i.e. their contents are defined by the implementation). The
``MessageRecipients`` interface represents any possible set of recipients: it's used when a piece of code doesn't
care who is going to get a message, just that someone does. The ``SingleMessageRecipient`` interface inherits from
``MessageRecipients`` and represents a handle to some specific individual receiver on the network. Whether they are
identified by IP address, public key, message router ID or some other kind of address is not exposed at this level.
``MessageRecipientGroup`` is not used anywhere at the moment but represents multiple simultaneous recipients. And
finally ``AllPossibleRecipients`` is used for network wide broadcast. It's also unused right now, outside of unit tests.

In memory implementation
------------------------

To ease unit testing of business logic, a simple in-memory messaging service is provided. To access this you can inherit
your test case class from the ``TestWithInMemoryNetwork`` class. This provides a few utility methods to help test
code that involves message passing.

You can run a mock network session in one of two modes:

- Manually "pumped"
- Automatically pumped with background threads

"Pumping" is the act of telling a mock network node to pop a message off its queue and process it. Typically you want
unit tests to be fast, repeatable and you want to be able to insert your own changes into the middle of any given
message sequence. This is what the manual mode is for. In this mode, all logic runs on the same thread (the thread
running the unit tests). You can create and use a node like this:

.. container:: codeset

   .. sourcecode:: kotlin

      val (aliceAddr, aliceNode) = makeNode(inBackground = false)
      val (bobAddr, bobNode) = makeNode(false)

      aliceNode.send("test.topic", aliceAddr, "foo")
      bobNode.pump(blocking = false)

.. note:: Currently only Kotlin examples are available for networking and protocol state machines. Java examples may
   follow later. Naming arguments in Kotlin like above is optional but sometimes useful to make code examples clearer.

The above code won't actually do anything because no message handler is registered for "test.topic" so the message will
go into a holding area. If/when we add a handler that can accept test.topic, the message will be delivered then.

Sometimes you don't want to have to call the pump method over and over again. You can use the ``runNetwork { .. }``
construct to fix this: any code inside the block will be run, and then all nodes you created will be pumped over and
over until all of them have reported that they have no work left to do. This means any ping-pongs of messages will
be run until everything settles.

You can see more examples of how to use this in the file ``InMemoryMessagingTests.kt``.

If you specify ``inBackground = true`` to ``makeNode`` then each node will create its own background thread which will
sit around waiting for messages to be delivered. Handlers will then be invoked on that background thread. This is a
more difficult style of programming that can be used to increase the realism of the unit tests by ensuring multiple
nodes run in parallel, just as they would on a real network spread over multiple machines.

Network Map Service
-------------------

Supporting the messaging layer is a network map service, which is responsible for tracking public nodes on the network.
Nodes have an internal component, the network map cache, which contains a copy of the network map. When a node starts up
its cache fetches a copy of the full network map, and requests to be notified of changes. The node then registers itself
with the network map service, and the service notifies subscribers that a new node has joined the network. Nodes do not
automatically deregister themselves, so far (for example) nodes going offline briefly for maintenance

Nodes submit signed changes to the map service, which then forwards them on to nodes which have requested to be notified
of changes. This process achieves basic consensus of the overall network map, although currently it has no formal
process for identifying or recovering from issues such as network outages. Later versions are planned to address this.

Registration change notifications contain a serial number, which indicates their relative ordering, similar to the
serial number on DNS records. These numbers must increase with each change, but are not expected to be sequential.
Changes are then signed by the party whom the node represents to confirm the association between party and node.
The change, signature and public key are then sent to the network map service, which verifies the signature and then
updates the network map accordingly.

The network map cache currently supports:

* Looking up nodes by service
* Looking up node for a party
* Suggesting a node providing a specific service, based on suitability for a contract and parties, for example suggesting
an appropriate interest rates oracle for a interest rate swap contract. Currently no recommendation logic is in place
(the code simply picks the first registered node that supports the required service), however.
