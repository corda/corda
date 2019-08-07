Flow framework internals
========================

Quasar
------

Instrumentation
^^^^^^^^^^^^^^^

Quasar rewrites bytecode to achieve a couple of things:

#. Collect the contents of the execution stack. It uses thread-local datastructures for this.
#. Create a way to jump from suspension points. It uses an un-catchable exception throw for this.
#. Create a way to jump into suspension points. It uses a sequence of switch statements for this.

To this end Quasar transforms the JVM bytecode of ``@Suspendable`` functions. Take the following as an example:

.. code-block:: kotlin

   @Suspendable
   fun s0(a): Int {
     val b = ..
     n1()
     s1()

     for (..) {
       val c = ..
       n2()
       s2(b)
     }

     n3()
     s3()

     return 1;
   }

   fun n1() { .. }
   fun n2() { .. }
   fun n3() { .. }
   fun n4() { .. }
   @Suspendable fun s1() { .. }
   @Suspendable fun s2(b) { .. }
   @Suspendable fun s3() { .. }

Quasar's javaagent, when loading bytecode, will look for functions with the ``@Suspendable`` annotation. Furthermore within these functions
it will look for callsites of other ``@Suspendable`` functions (which is why it's important to annotate interface/abstract class methods
**as well as** implementations). Note how ``n1``-``n4`` are thus not instrumented, and their callsites aren't relevant in the instrumentation
of ``s0``.

Disregarding any potential optimizations, quasar will then do the following transformation of ``s0``:

.. note:: The following code is pseudo-Kotlin code and includes non-existent constructs like arbitrary code labels and ``goto``.

.. code-block:: kotlin

   // Quasar uses this annotation to store some metadata about the function, and to check whether the function has been instrumented already
   @Instrumented
   fun s0(a): Int {
     // A variable to temporarily store s0's return value later on
     var __return = null
     // A variable to indicate whether we are being resumed (we are jumping into a suspension's continuation), or this is a "regular" call.
     lateinit var __resumed: Boolean
     // Retrieve the Quasar-internal execution stack, which is stored in a thread-local.
     var __stack = co.paralleluniverse.fibers.Stack.getStack()
     if (__stack == null) {
       // There's no stack, execute as a regular function
       goto lMethodStart
     }

     // We are being resumed, we are jumping into the suspension point
     __resumed = true
     // Retrieve the integer that indicates which part of this function we should be jumping into, stored in a thread-local.
     val __entry = co.paralleluniverse.fibers.Stack.nextMethodEntry()
     when (__entry) {
       0 -> {
         TODO
       }
       1 -> {
         TODO
       }
       2 -> {
         TODO
       }
       else -> {
         // The entry value is not recognized, the function may be called in a non-suspending capacity.
         val __isFunctionCalledAsSuspendable = co.paralleluniverse.fibers.Stack.isFirstInStackOrPushed()
         if (_isFunctionCalledAsSuspendable) {
           goto lMethodStart
         }
         __stack = null
       }
     }

     // The first code block, starting from the original non-transformed function start.
     lMethodStart:
     // This try-catch handles the Quasar-specific SuspendExecution exception. Quasar prevents the catching of this exception in user code.
     try {
       __resumed = false
       val b = ..
       TODO
     } catch (e: SuspendExecution {
       TODO
     }
   }

.. note:: The Quasar javaagent code doing the above rewriting can be found
   `here <https://github.com/puniverse/quasar/blob/db0ac29f55bc0515023d67ab86a2178c5e6eeb94/quasar-core/src/main/java/co/paralleluniverse/fibers/instrument/InstrumentMethod.java#L328>`_.
   Note that only the main parts of the instrumentation are shown above, the actual transformation is more complex and involves handling
   corner cases and optimizations.

Fibers
^^^^^^

The above instrumentation allows the implementation of *co-operative* scheduling. That is, ``@Suspendable`` code can yield its execution by
throwing a ``SuspendExecution`` exception. This exception throw takes care of handing the control flow to a top-level try-catch, which then
has access to the thread-locally constructed execution stack, as well as a way to return to the suspension point using the "method entry"
list.

A ``Fiber`` thus is nothing more than a data structure holding the execution stack, the method entry list, as well as various bookkeeping
data related to the management of the ``Fiber``, e.g. its state enum or identifier.

The main try-catch that handles the yielding may be found `here <https://github.com/puniverse/quasar/blob/db0ac29f55bc0515023d67ab86a2178c5e6eeb94/quasar-core/src/main/java/co/paralleluniverse/fibers/Fiber.java#L790>`_.

.. note:: For those adventurous enough to explore the implementation, the execution stack and method entry list are merged into two growing
   arrays in ``Stack``, one holding ``Object`` s (``dataObject``, for structured objects), the other holding ``long`` s (``dataLong``, for
   primitive values). The arrays always have the same length, and they both contain values for each stack frame. The primitive stack
   additionally has a "metadata" slot for each stack frame, this is where the "method entry" value is put, as well as frame size data.

.. _flow_internals_checkpoints_ref:

Checkpoints
-----------

The main idea behind checkpoints is to utilize the ``Fiber`` data structure and treat it as a serializable object capturing the state of a
running computation. Whenever a Corda-suspendable API is hit, we capture the execution stack and corresponding entry list, and serialize
it using `Kryo <https://github.com/EsotericSoftware/kryo>`_, a reflection-based serialization library capable of serializing unstructured
data. We thus get a handle to an arbitrary suspended computation.

In the flow state machine there is a strict separation of the user-code's state, and the flow framework's internal state. The former is the
serialized ``Fiber``, and the latter consists of structured objects.

The definition of a ``Checkpoint`` can be found `here <https://github.com/corda/corda/blob/dc4644643247d86b14165944f6925c2d2561eabc/node/src/main/kotlin/net/corda/node/services/statemachine/StateMachineState.kt#L55>`_.

The "user state" can be found in ``FlowState``. It is either

#. ``Unstarted``: in this case there's no ``Fiber`` to serialize yet, we serialize the ``FlowLogic`` instead.
#. ``Started``: in this case the flow has been started already, and has been suspended on some IO. We store the ``FlowIORequest`` and the
   serialized ``Fiber``.

The rest of the ``Checkpoint`` deals with internal bookkeeping. Sessions, the subflow-stack, errors. Note how all data structures are
read-only. This is deliberate, to enable easier reasoning. Any "modification" of the checkpoint therefore implies making a shallow copy.

The state machine
-----------------

The internals of the flow framework were designed as a state machine. A flow is a strange event loop that has a state, and goes through
state transitions triggered by events. The transitions may be either

#. User transitions, when we hand control to user-defined code in the cordapp. This may transition to a suspension point, the end of the
   flow, or may abort exceptionally.
#. Internal transitions, where we keep strict track of side-effects and failure conditions.

The core data structures of the state machine are:

#. ``StateMachineState``: this is the full state of the state machine. It includes the ``Checkpoint`` (the persisted part of the state), and
   other non-persisted state, most importantly the list of pending ``DeduplicationHandler`` s, to be described later.
#. ``Event``: Every state transition is triggered by one of these. These may be external events, notifying the state machine of something,
   or internal events, for example suspensions.
#. ``Action``: These are created by internal state transitions. These transitions do not inherently execute any side-effects, instead, they
   create a list of ``Action`` s, which are later executed.
#. ``FlowContinuation``: indicates how the state machine should proceed after a transition. It can resume to user code, throw an exception,
   keep processing events or abort the flow completely.

The state machine is a **pure** function that when given an ``Event`` and an initial ``StateMachineState`` returns the next state, a list of
``Action`` s to execute, and a ``FlowContinuation`` to indicate how to proceed:

.. code-block:: kotlin

   // https://github.com/corda/corda/blob/c04a448bf391fb73f9b60cc41e8b5f0c23f81470/node/src/main/kotlin/net/corda/node/services/statemachine/transitions/TransitionResult.kt#L15
   data class TransitionResult(
           val newState: StateMachineState,
           val actions: List<Action> = emptyList(),
           val continuation: FlowContinuation = FlowContinuation.ProcessEvents
   )

   // https://github.com/corda/corda/blob/c04a448bf391fb73f9b60cc41e8b5f0c23f81470/node/src/main/kotlin/net/corda/node/services/statemachine/transitions/StateMachine.kt#L12
   fun transition(event: Event, state: StateMachineState): TransitionResult

The top-level entry point for the state machine transitions is in ``TopLevelTransition``.

As an example let's examine message delivery. This transition will be triggered by a ``DeliverSessionMessage`` event, defined like this:

.. code-block:: kotlin

    data class DeliverSessionMessage(
            val sessionMessage: ExistingSessionMessage,
            override val deduplicationHandler: DeduplicationHandler,
            val sender: Party
    ) : Event(), GeneratedByExternalEvent

The event then goes through ``TopLevelTransition``, which then passes it to ``DeliverSessionMessageTransition``. This transition inspects
the event, then does the relevant bookkeeping, updating sessions, buffering messages etc. Note that we don't do any checkpoint persistence,
and we don't return control to the user code afterwards, we simply schedule a ``DoRemainingWork`` and return a ``ProcessEvents``
continuation. This means that it's going to be the next transition that decides whether the received message is "relevant" to the current
suspension, and whether control should thus be returned to user code with the message.

FlowStateMachineImpl
--------------------

The state machine is a pure function, so what is the "driver" of it, that actually executes the transitions and side-effects? This is what
``FlowStateMachineImpl`` is doing, which is a ``Fiber``. This class requires great care when it's modified, as the programmer must be aware
of what's on the stack, what fields get persisted as part of the ``Checkpoint``, and how the control flow is wired.

The usual way to implement state machines is to create a simple event loop that keeps popping events from a queue, and executes the
resulting transitions. With flows however this isn't so simple, because control must be returned to suspending operations. Therefore the
eventloop is split up into several smaller eventloops, executed when "we get the chance", i.e. when users call API functions. Whenever the
flow calls a Flow API function, control is handed to the flow framework, that's when we can process events, until a ``FlowContinuation``
indicates that control should be returned to user code.

There are two functions that aid the above:

#. ``FlowStateMachineImpl.processEventsUntilFlowIsResumed``: as the name suggests this is a loop that keeps popping and processing events
   from the flow's event queue, until a ``FlowContinuation.Resume`` or some continuation other than ``ProcessEvents`` is returned.
#. ``FlowStateMachineImpl.processEventImmediately``: this function skips the event queue and processes an event immediately. There are
   certain transitions (e.g. subflow enter/exit) that must be done this way, otherwise the event ordering can cause problems.

The two main functions that call the above are the top-level ``run``, which is the entry point of the flow, and ``suspend``, which every
blocking API call eventually calls.

Suspensions
-----------

Let's take a look at ``suspend``, which is the most delicate/brittle function in this class, and most probably the whole flow framework.
Examining it will reveal a lot about how flows and fibers work.

.. code-block:: kotlin

    @Suspendable
    override fun <R : Any> suspend(ioRequest: FlowIORequest<R>, maySkipCheckpoint: Boolean): R {

First off, the type signature. We pass in a ``FlowIORequest<R>``, which is an encapsulation of the IO action we're about to suspend on. It
is a sealed class with members like ``Send``/``Receive``/``ExecuteAsyncOperation``. It is serializable, and will be part of the
``Checkpoint``. In fact, it is doubly-serialized, as it is in ``FlowState`` in a typed form, but is also present in the Fiber's stack, as a
part of ``suspend``'s stack frame.

We also pass a ``maySkipCheckpoint`` boolean which if true will prevent the checkpoint from being persisted.

The function returns ``R``, but the runtime control flow achieving this "return" is quite tricky. When the Fiber suspends a
``SuspendExecution`` exception will be thrown, and when the fiber is resumed this ``suspend`` function will be re-entered, however this time
in a "different capacity", indicated by Quasar's implicitly stored method entry, which will jump to the end of the suspension. This is
repeated several times as this function has two suspension points, one of them possibly executing multiple times, as we will see later.

.. code-block:: kotlin

        val serializationContext = TransientReference(getTransientField(TransientValues::checkpointSerializationContext))
        val transaction = extractThreadLocalTransaction()

These lines extract some data required for the suspension. Note that both local variables are ``TransientReference`` s, which means the
referred-to object will not be serialized as part of the stack frame. During resumption from a deserialized checkpoint these local variables
will thus be null, however at that point these objects will not be required anymore.

The first line gets the serialization context from a ``TransientValues`` datastructure, which is where all objects live that are required
for the flow's functioning but which we don't want to persist. This means all of these values must be re-initialized each time we are
restoring a flow from a persisted checkpoint.

.. code-block:: kotlin

        parkAndSerialize { _, _ ->

This is the Quasar API that does the actual suspension. The passed in lambda will not be executed in the current ``suspend`` frame, but
rather is stored temporarily in the internal ``Fiber`` structure, and will be run in the outer Quasar try-catch as a "post park" action
after the catch of the ``SuspendExecution`` exception. See `Fiber.java <https://github.com/puniverse/quasar/blob/db0ac29f55bc0515023d67ab86a2178c5e6eeb94/quasar-core/src/main/java/co/paralleluniverse/fibers/Fiber.java#L804>`_ for details.

This means that within this lambda the Fiber will have already technically parked, but it hasn't yet properly yielded to the enclosing
scheduler.

.. code-block:: kotlin

            setLoggingContext()

Thread-locals are treated in a special way when Quasar suspends/resumes. Through use of `reflection and JDK-internal unsafe operations <https://github.com/puniverse/quasar/blob/db0ac29f55bc0515023d67ab86a2178c5e6eeb94/quasar-core/src/main/java/co/paralleluniverse/concurrent/util/ThreadAccess.java>`_
it accesses all ThreadLocals in the current thread and swaps them with ones stored in the Fiber data structure. In essence for each thread
that executes as a Fiber we have two sets of thread locals, one set belongs to the original "non-Quasar" thread, and the other belongs to
the Fiber. During Fiber execution the latter is active, this is swapped with the former during suspension, and swapped back during resume.
Note that during resume these thread-locals may actually be restored to a *different* thread than the original.

In the ``parkAndSerialize`` closure the Fiber is partially parked, and at this point the thread locals are already swapped out. This means
that data stored in ``ThreadLocal`` s that we still need must be re-initialized somehow. In the above case this is the logging MDC.

.. code-block:: kotlin

            // Will skip checkpoint if there are any idempotent flows in the subflow stack.
            val skipPersistingCheckpoint = containsIdempotentFlows() || maySkipCheckpoint

            contextTransactionOrNull = transaction.value
            val event = try {
                Event.Suspend(
                        ioRequest = ioRequest,
                        maySkipCheckpoint = skipPersistingCheckpoint,
                        fiber = this.checkpointSerialize(context = serializationContext.value)
                )
            } catch (exception: Exception) {
                Event.Error(exception)
            }

A couple of things happen here. First we determine whether this suspension's subflow stack contains an ``IdempotentFlow``, to determine
whether to skip checkpoints. An idempotent flow is a subflow that's safe to replay from the beginning. This means that no checkpoint will be
persisted during its execution, as replaying from the previous checkpoint should yield the same results semantically. As an example the
notary client flow is an ``IdempotentFlow``, as notarisation is idempotent, and may be safely replayed.

We then set another thread-local, the database transaction, which was also swapped out during the park, and we made it available to the
closure temporarily using a ``TransientReference`` earlier. The database transaction is used during serialization of the fiber and
persistence of the checkpoint.

We then create the ``Suspend`` event, which includes the IO request and the serialized Fiber. If there's an exception during serialization
we create an ``Error`` event instead. Note how every condition, including error conditions are treated as "normal control flow" in the state
machine, we must be extra careful as these conditions are also exposed to the user and are part of our API guarantees.

.. code-block:: kotlin

            // We must commit the database transaction before returning from this closure otherwise Quasar may schedule
            // other fibers, so we process the event immediately
            val continuation = processEventImmediately(
                    event,
                    isDbTransactionOpenOnEntry = true,
                    isDbTransactionOpenOnExit = false
            )
            require(continuation == FlowContinuation.ProcessEvents){"Expected a continuation of type ${FlowContinuation.ProcessEvents}, found $continuation "}

We first process the suspension event ASAP, as we must commit the underlying database transaction before the closure ends.

.. note::

   The call to ``processEventImmediately`` here reveals why the transition execution is structured in such an unintuitive way, why we are
   not simply using an event loop. In an earlier iteration of the flow framework a separate thread pool was handling events and state
   transitions, the state machine transitions' execution was completely offloaded, and the Fiber itself was only concerned with the
   execution of user code and creation of suspension events.

   However later it turned out that under any considerable load this structuring results in heavy resource leakage, and in the case of
   database transactions, deadlocks. The reason for this is simply that resource management is often tied to thread lifetime, for example in
   the case of serialization buffers, network buffers, database connections. Quasar multiplexes threads across many many more Fibers,
   however this also explodes thread-bound resources allocated/retained, which are now Fiber-bound. This means that if we want to take
   advantage of Quasar's green threading we must make sure to release any thread-local resources before yielding, otherwise we will leak.

   To give a specific example, if we processed the above ``Suspend`` event in another thread or even just after this closure, the underlying
   database connection would leak through a proper Fiber yield, meaning it would not be closed until the Fiber is scheduled again or until
   the processing thread picks it up and closes it. In the case of database transactions we use Hikari to pool the connections, which means
   that the flow framework would quickly exhaust the connection pool, which would thus cause a proper thread block of the Fiber-executing
   threads trying to acquire a connection. This in turn means there would be absolutely no chance of the fibers retaining the connections
   getting scheduled again, effectively deadlocking the executor threadpool.

.. code-block:: kotlin

            unpark(SERIALIZER_BLOCKER)
        }
        return uncheckedCast(processEventsUntilFlowIsResumed(
                isDbTransactionOpenOnEntry = false,
                isDbTransactionOpenOnExit = true
        ))

As the last step in the park closure we unpark the Fiber we are currently parking. This effectively causes an "immediate" re-enter of the
fiber, and therefore the ``suspend`` function, but this time jumping over the park and executing the next statement. Of course this re-enter
may happen much later, perhaps even on a different thread.

We then enter a mini event-loop, which also does Quasar yields, processing the flow's event queue until a transition continuation indicates
that control can be returned to user code . Practically this means that when a flow is waiting on an IO action it won't actually be blocked
in the ``parkAndSerialize`` call, but rather in this event loop, popping from the event queue.

.. note::

   The ``processEvent*`` calls do explicit checks of database transaction state on entry and exit. This is because Quasar yields make
   reasoning about resource usage difficult, as they detach resource lifetime from lexical scoping, or in fact any other scoping that
   programmers are used to. These internal checks ensure that we are aware of which code blocks have a transaction open and which ones
   don't. Incidentally these checks also seem to catch instrumentation/missing ``@Suspendable``-annotation problems.

Event processing
----------------

The processing of an event consists of two steps:

#. Calculating a transition. This is the pure ``StateMachineState`` + ``Event`` -> ``TransitionResult`` function.
#. Executing the transition. This is done by a ``TransitionExecutor``, which in turn uses an ``ActionExecutor`` for individual ``Action``s.

This structuring allows the introspection and interception of state machine transitions through the registering of ``TransitionExecutor``
interceptors. These interceptors are ``TransitionExecutor`` s that have access to a delegate. When they receive a new transition they can
inspect it, pass it to the delegate, and do something specific to the interceptor.

For example checkpoint deserializability is checked by such an `interceptor <https://github.com/corda/corda/blob/76d738c4529fd7bdfabcfd1b61d500f9259978f7/node/src/main/kotlin/net/corda/node/services/statemachine/interceptors/FiberDeserializationCheckingInterceptor.kt#L18>`_.
It inspects a transition, and if it contains a Fiber checkpoint then it checks whether it's deserializable in a separate thread.

The transition calculation is done in the ``net.corda.node.services.statemachine.transitions`` package, the top-level entry point being
``TopLevelTransition``. There is a ``TransitionBuilder`` helper that makes the transition definitions a bit more readable. It contains a
``currentState`` field that may be updated with new ``StateMachineState`` instances as the event is being processed, and has some helper
functions for common functionality, for example for erroring the state machine with some error condition.

Here are a couple of highlighted transitions:

Suspend
^^^^^^^

Handling of ``Event.Suspend`` is quite straightforward and is done `here <https://github.com/corda/corda/blob/26855967989557e4c078bb08dd528231d30fad8b/node/src/main/kotlin/net/corda/node/services/statemachine/transitions/TopLevelTransition.kt#L143>`_.
We take the serialized ``Fiber`` and the IO request and create a new checkpoint, then depending on whether we should persist or not we
either simply commit the database transaction and schedule a ``DoRemainingWork`` (to be explained later), or we persist the checkpoint, run
the ``DeduplicationHandler`` inside-tx hooks, commit, then run the after-tx hooks, and schedule a ``DoRemainingWork``.

Every checkpoint persistence implies the above steps, in this specific order.

DoRemainingWork
^^^^^^^^^^^^^^^

This is a generic event that simply tells the state machine: inspect your current state, and decide what to do, if anything. Using this
event we can break down transitions into a <modify state> and <inspect and do stuff> transition, which compose well with other transitions,
as we don't need to add special cases everywhere in the state machine.

As an example take error propagation. When a flow errors it's put into an "errored" state, and it's waiting for further instructions. One
possibility is the triggering of error propagation through the scheduling of ``Event.StartErrorPropagation``. Note how the handling of this
event simply does the following:

.. code-block:: kotlin

                    currentState = currentState.copy(
                            checkpoint = currentState.checkpoint.copy(
                                    errorState = errorState.copy(propagating = true)
                            )
                    )
                    actions.add(Action.ScheduleEvent(Event.DoRemainingWork))

It marks the error state as ``propagating = true`` and schedules a ``DoRemainingWork``. The processing of that event in turn will detect
that we are errored and propagating, and there are some errors that haven't been propagated yet. It then propagates those errors and updates
the "propagated index" to indicate all errors have been dealt with. Subsequent ``DoRemainingWork``s will thus do nothing. However, in case
some other error condition or external event adds another error to the flow, we would automatically propagate that too, we don't need to
write a special case for it.

Most of the state machine logic is therefore about the handling ``DoRemainingWork``. Another example is resumptions due to an IO request
completing in some way. ``DoRemainingWork`` checks whether we are currently waiting for something to complete e.g. a
``FlowIORequest.Receive``. It then checks whether the state contains enough data to complete the action, in the receive case this means
checking the relevant sessions for buffered messages, and seeing whether those messages are sufficient to resume the flow with.

Transition execution
^^^^^^^^^^^^^^^^^^^^

Once the transition has been calculated the transition is passed to the flow's ``TransitionExecutor``. The main executor is
``TransitionExecutorImpl``, which executes the transition's ``Action`` s, and handles errors by manually erroring the flow's state. This is
also when transition interceptors are triggered.

Errors
^^^^^^

An error can manifest as either the whole flow erroring, or a specific session erroring. The former means that the whole flow is blocked
from resumption, and it will end up in the flow hospital. A session erroring blocks only that specific session. Any interaction with this
session will in turn error the flow. Session errors are created by a remote party propagating an error to our flow.

How to modify the state machine
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Let's say we wanted to change the session messaging protocol. How would we go about changing the state machine?

The session logic is defined by

#. Session message definitions, see the ``SessionMessage`` sealed class.
#. Session state definitions, see the ``SessionState`` sealed class. This is the state we store per established/to-be-established session
   with a ``Party``.
#. Session state transitions, see ``DeliverSessionMessageTransition``.

Let's say we wanted to add more handshake steps. To do this we need to add new types of ``SessionMessage`` s as required, new
``SessionState`` s, and cases to handle state transitions in ``DeliverSessionMessageTransition``. This handles the receive path, to handle
send paths ``StartedFlowTransition.sendTransition`` needs modifying, this is the transition triggered when the flow suspends on a send.

Atomicity
---------

DeduplicationHandler
^^^^^^^^^^^^^^^^^^^^

The flow framework guarantees atomicity of processing incoming events. This means that a flow or the node may be stopped at any time, even
during processing of an event and on restart the node will reconstruct the correct state of the flows and will proceed as if nothing
happened.

To do this each external event is given two hooks, one inside the database transaction committing the next checkpoint, and one after the
commit, to enable implementation of exactly-once delivery on top of at-least-once. These hooks can be found on the ``DeduplicationHandler``
interface:

.. code-block:: kotlin

   /**
    * This handler is used to implement exactly-once delivery of an external event on top of an at-least-once delivery. This is done
    * using two hooks that are called from the event processor, one called from the database transaction committing the
    * side-effect caused by the external event, and another one called after the transaction has committed successfully.
    *
    * For example for messaging we can use [insideDatabaseTransaction] to store the message's unique ID for later
    * deduplication, and [afterDatabaseTransaction] to acknowledge the message and stop retries.
    *
    * We also use this for exactly-once start of a scheduled flow, [insideDatabaseTransaction] is used to remove the
    * to-be-scheduled state of the flow, [afterDatabaseTransaction] is used for cleanup of in-memory bookkeeping.
    *
    * It holds a reference back to the causing external event.
    */
   interface DeduplicationHandler {
       /**
        * This will be run inside a database transaction that commits the side-effect of the event, allowing the
        * implementor to persist the event delivery fact atomically with the side-effect.
        */
       fun insideDatabaseTransaction()

       /**
        * This will be run strictly after the side-effect has been committed successfully and may be used for
        * cleanup/acknowledgement/stopping of retries.
        */
       fun afterDatabaseTransaction()

       /**
        * The external event for which we are trying to reduce from at-least-once delivery to exactly-once.
        */
       val externalCause: ExternalEvent
   }

Let's take message delivery as an example. From the flow framework's perspective we are assuming at least once delivery, and in order
delivery. When a message is received a corresponding ``DeduplicationHandler`` is created. The hook inside the database transaction persists
the message ID, and the hook after acknowledges it, stopping potential retries. If the node crashes before the transaction commits then the
message will be redelivered, and if it crashes after it will be deduplicated based on the ID table.

We also use this for deduplicating scheduled flow starts, the inside hook removes the scheduled StateRef, and the after hook cleans up
in-memory bookkeeping.

We could also use this for deduplicating RPC flow starts. A deduplication ID would be generated (and potentially stored) on the client,
persisted on the node in the inside-tx hook, and the start would be acked afterwards, removing the ID from the client (and stopping
retries).

Internally a list of pending ``DeduplicationHandler`` s is accumulated in the state machine in ``StateMachineState``. When the next
checkpoint is persisted the corresponding ``insideDatabaseTranscation`` hooks are run, and once the checkpoint is committed the
``afterDatabaseTransaction`` hooks are run.

In-memory flow retries
^^^^^^^^^^^^^^^^^^^^^^

Tracking of these handlers also allows us to do in-memory retries of flows. To do this we need to re-create the flow from the last
checkpoint and retry external events internally. For every flow we have two lists of such "events", one is the yet-unprocessed event queue
of the flow, and one is the already processed but still pending list of ``DeduplicationHandler`` s. The concatenation of these events gives
us a handle on the list of events relevant to the flow since the last persisted checkpoint, so we just need to re-process these events. All
of these events go through the ``StateMachineManager``, which is where the retry is handled too.

.. note::

   There may be cases where there is no checkpoint yet for a flow that needs retrying. In this case the re-processing of the events is
   sufficient, as one of those events will be the starting of the flow, or a delivery of a flow initiation message. So it all works out!

Deduplication
^^^^^^^^^^^^^

Full message deduplication is more complex, what we've discussed so far only dealt with the state machine bits.

When we receive a message from Artemis it is eventually handled by
``P2PMessagingClient.deliver``, which consults the ``P2PDeduplicator`` class to determine whether the message is a duplicate.
``P2PDeduplicator`` holds two data structures:

#. ``processedMessages``: the persisted message ID table. Any message ID in this table must have been committed together with a checkpoint
   that includes the side-effects caused by the message.
#. ``beingProcessedMessages``: an in-memory map holding the message IDs until they are being processed and committed.

These two data structures correspond to the two ``DeduplicationHandler`` hooks of each message. ``insideDatabaseTransaction`` adds to the
``processedMessages`` map, ``afterDatabaseTransaction`` removes from ``beingProcessedMessages``

The indirection through the in-memory map is needed because Artemis may redeliver unacked messages in certain situations, and at that point
the message may still be "in-flight", i.e. the ID may not be committed yet.

If the message isn't a duplicate then it's put into ``beingProcessedMessages`` and forwarded to the state machine manager, which then
forwards it to the right flow or constructs one if this is an initiating message. When the next checkpoint of the relevant flow is persisted
the message is "finalized" as discussed, using its ``DeduplicationHandler``.

Flow hospital
-------------

The flow hospital is a place where errored flows end up. This is done using an interceptor that detects error transitions and notifies the
hospital.

The hospital can decide what to do with the flow: restart it from the last persisted checkpoint using an in-memory retry, keep the flow
around pending either manual intervention or a restart of the node (in which case it will be retried from the last persisted checkpoint on
start), or trigger error propagation, which makes the error permanent and notifies other parties the flow has sessions with of the failure.

This is where we can do special logic to handle certain error conditions like notary failures in a specific way e.g. by retrying.
