Checkpoint Tooling
================

This page contains information about checkpoint tooling. These tools can be used to debug the causes of stuck flows.

Before reading this page, please ensure you understand the mechanics and principles of Corda Flows by reading :doc:`key-concepts-flows` and :doc:`flow-state-machines`.
It is also recommended that you understand the purpose and behaviour of the :doc:`node-flow-hospital` in relation to *checkpoints* and flow recovery.
An advanced explanation of :ref:`*checkpoints* <flow_internals_checkpoints_ref>` within the flow state machine can be found here: :doc:`contributing-flow-internals`.

.. note:: As a recap,

    A flow *checkpoint* is a serialised snapshot of the flow's stack frames and any objects reachable from the stack. Checkpoints are saved to
    the database automatically when a flow suspends or resumes, which typically happens when sending or receiving messages. A flow may be replayed
    from the last checkpoint if the node restarts. Automatic checkpointing is an unusual feature of Corda and significantly helps developers write
    reliable code that can survive node restarts and crashes. It also assists with scaling up, as flows that are waiting for a response can be flushed
    from memory.

The checkpoint tools available are:

- :ref:`Checkpoint dumper <checkpoint_dumper>`
- :ref:`Checkpoint agent <checkpoint_agent>`

.. _checkpoint_dumper:

Checkpoint dumper
~~~~~~~~~~~~~~~~~

The checkpoint dumper outputs information about flows running on a node. This is useful for diagnosing the causes of stuck flows. Using the generated output,
corrective actions can be taken to resolve the issues flows are facing. One possible solution, is ending a flow using the ``flow kill`` command.

.. warning:: Deleting checkpoints manually or via ``flow kill``/```killFlow`` can lead to an inconsistent ledger among transacting parties. Great care
             and coordination with a flow's counterparties must be taken to ensure that a initiating flow and flows responding to it are correctly
             removed. This experience will be improved in the future. Making it easier to kill flows while notifying their counterparties.

To retrieve this information, execute ``run dumpCheckpoints`` in the node's shell. The command creates a zip and generates a JSON file for each flow.

- Each file follows the naming format ``<flow name>-<flow id>.json`` (for example, ``CashIssueAndPaymentFlow-90613d6f-be78-41bd-98e1-33a756c28808.json``).
- The zip is placed into the ``logs`` directory of the node and is named ``checkpoints_dump-<date and time>.zip`` (for example, ``checkpoints_dump-20190812-153847``).

Below are some of the more important fields included in the output:

- ``flowId``: The id of the flow
- ``topLevelFlowClass``: The name of the original flow that was invoked (by RPC or a service)
- ``topLevelFlowLogic``: Detailed view of the top level flow
- ``flowCallStackSummary``: A summarised list of the current stack of sub flows along with any progress tracker information
- ``suspendedOn``: The command that the flow is suspended on (e.g. ``SuspendAndReceive``) which includes the ``suspendedTimestamp``
- ``flowCallStack`` A detailed view of the of the current stack of sub flows

.. _checkpoint_dumper_sample_output:

Sample output
-------------

Below is an example of the JSON output:

.. sourcecode:: json

    {
      "flowId" : "90613d6f-be78-41bd-98e1-33a756c28808",
      "topLevelFlowClass" : "net.corda.finance.flows.CashIssueAndPaymentFlow",
      "topLevelFlowLogic" : {
        "amount" : "10.00 USD",
        "issueRef" : "MTIzNA==",
        "recipient" : "O=BigCorporation, L=New York, C=US",
        "anonymous" : true,
        "notary" : "O=Notary, L=London, C=GB"
      },
      "flowCallStackSummary" : [
        {
          "flowClass" : "net.corda.finance.flows.CashIssueAndPaymentFlow",
          "progressStep" : "Paying recipient"
        },
        {
          "flowClass" : "net.corda.finance.flows.CashPaymentFlow",
          "progressStep" : "Generating anonymous identities"
        },
        {
          "flowClass" : "net.corda.confidential.SwapIdentitiesFlow",
          "progressStep" : "Awaiting counterparty's anonymous identity"
        }
      ],
      "suspendedOn" : {
        "sendAndReceive" : [
          {
            "session" : {
              "peer" : "O=BigCorporation, L=New York, C=US",
              "ourSessionId" : -5024519991106064492
            },
            "sentPayloadType" : "net.corda.confidential.SwapIdentitiesFlow$IdentityWithSignature",
            "sentPayload" : {
              "identity" : {
                "class" : "net.corda.core.identity.PartyAndCertificate",
                "deserialized" : "O=BankOfCorda, L=London, C=GB"
              },
              "signature" : "M5DN180OeE4M8jJ3mFohjgeqNYOWXzR6a2PIclJaWyit2uLnmJcZatySoSC12b6e4rQYKIICNFUXRzJnoQTQCg=="
            }
          }
        ],
        "suspendedTimestamp" : "2019-08-12T15:38:39Z",
        "secondsSpentWaiting" : 7
      },
      "flowCallStack" : [
        {
          "flowClass" : "net.corda.finance.flows.CashIssueAndPaymentFlow",
          "progressStep" : "Paying recipient",
          "flowLogic" : {
            "amount" : "10.00 USD",
            "issueRef" : "MTIzNA==",
            "recipient" : "O=BigCorporation, L=New York, C=US",
            "anonymous" : true,
            "notary" : "O=Notary, L=London, C=GB"
          }
        },
        {
          "flowClass" : "net.corda.finance.flows.CashPaymentFlow",
          "progressStep" : "Generating anonymous identities",
          "flowLogic" : {
            "amount" : "10.00 USD",
            "recipient" : "O=BigCorporation, L=New York, C=US",
            "anonymous" : true,
            "issuerConstraint" : [ ],
            "notary" : "O=Notary, L=London, C=GB"
          }
        },
        {
          "flowClass" : "net.corda.confidential.SwapIdentitiesFlow",
          "progressStep" : "Awaiting counterparty's anonymous identity",
          "flowLogic" : {
            "otherSideSession" : {
              "peer" : "O=BigCorporation, L=New York, C=US",
              "ourSessionId" : -5024519991106064492
            },
            "otherParty" : null
          }
        }
      ],
      "origin" : {
        "rpc" : "bankUser"
      },
      "ourIdentity" : "O=BankOfCorda, L=London, C=GB",
      "activeSessions" : [ ],
      "errored" : null
    }

.. _checkpoint_agent:

Checkpoint Agent
~~~~~~~~~~~~~~~~

The Checkpoint Agent is a very low level diagnostics tool that can be used to output the type, size and content of flow *checkpoints* at node runtime.
It is primarily targeted at users developing and testing code that may exhibit flow mis-behaviour (preferably before going into production).

For a given flow *checkpoint*, the agent outputs:

    1. Information about the checkpoint such as its ``id`` (also called a ``flow id``) that can be used to correlate with that flows lifecycle details in the main Corda logs.
    2. A nested hierarchical view of its reachable objects (indented and tagged with depth and size) and their associated sizes, including the state
       of any flows held within the checkpoint.

Diagnostics information is written to standard log files (eg. log4j2 configured logger).

This tool is particularly useful when used in conjunction with the ``dumpCheckpoints`` CRaSH shell command to troubleshoot and identify potential
problems associated with checkpoints for flows that appear to not be completing.

The checkpoint agent can be downloaded from `here <https://software.r3.com/artifactory/corda-releases/net/corda/corda-tools-checkpoint-agent/>`_.

To run simply pass in the following jar to the JVM used to start a Corda node: ``-javaagent:<PATH>/checkpoint-agent.jar[=arg=value,...]``

.. warning:: This tool requires additional memory footprint and we recommended a minimal heap size of at least 1Gb.

The agent can be customised with a number of optional parameters described below.

.. note:: When using the gradle plugin utilities for deploying and running nodes, ensure the checkpoint agent jar is correctly passed to capsule as follows:
    ``-Dcapsule.jvm.args=-javaagent:checkpoint-agent.jar[=arg=value,...]``

Configuration
-------------

The checkpoint agent can be started with the following optional parameters:

.. code-block:: shell

    checkpoint-agent.jar=[instrumentType=<read|write>],[instrumentClassname=<CLASSNAME>],[minimumSize=<MIN_SIZE>],[maximumSize=<MAX_SIZE>, [graphDepth=<DEPTH>], [printOnce=<true|false>]

* ``instrumentType``: whether to output checkpoints on read or write. Possible values: [read, write]. Default: read.
* ``instrumentClassname``: specify the base type of objects to log. The default setting is to process all *Flow* object types. Default: net.corda.node.services.statemachine.FlowStateMachineImpl.
* ``minimumSize``: specifies the minimum size (in bytes) of objects to log. Default: 8192 bytes (8K)
* ``maximumSize``: specifies the maximum size (in bytes) of objects to log. Default: 20000000 bytes (20Mb)
* ``graphDepth``: specifies how many levels deep to display the graph output. Default: unlimited
* ``printOnce``: if true, will display a full object reference (and its sub-graph) only once. Otherwise an object will be displayed repeatedly as referenced. Default: true

These arguments are passed to the JVM along with the agent specification. For example:

.. code-block:: shell

    -javaagent:<PATH>/checkpoint-agent.jar=instrumentClassname=net.corda.vega.flows.SimmFlow,instrumentType=read,minimumSize=10240,maximumSize=512000,graphDepth=6,printOnce=false

.. note:: Arguments may be passed into the agent in any order and should **not** contain spaces between them.

Checkpoint Dump support
-----------------------

When used in combination with the ``dumpCheckpoints`` shell command (see :ref:`Checkpoint Dumper <checkpoint_dumper>`),
the checkpoint agent will automatically output additional diagnostic information for all checkpoints dumped by the aforementioned tool.

You should therefore see two different output files upon invoking the checkpoint dumper command:

* ``<NODE_BASE>\logs\checkpoints_dump-<date>.zip`` contains zipped JSON representation of checkpoints (from ``dumpCheckpoints`` shell command)
* ``<NODE_BASE>\logs\checkpoints_agent-<date>.log`` contains output from this agent tool (types and sizes of a checkpoint stack)

.. note:: You will only see a separate `checkpoints_agent-<date>.log` file if you configure a separate log4j logger as described below.
   Otherwise all diagnostics logging will be routed to the standard Corda node log file: ``node-<hostname>.log``.

If you **only** wish to log checkpoint data for failing flows, start the checkpoint agent with the following arguments:

.. code-block:: shell

    checkpoint-agent.jar=instrumentType=read,instrumentClassname=NONE

and use the ``dumpCheckpoints`` shell command to trigger diagnostics collection.

.. warning:: The checkpoint agent JAR file must be called "checkpoint-agent.jar" as the checkpoint dump support code uses Java reflection to
   determine whether the VM has been instrumented or not at runtime.

Logging configuration
---------------------

The agent will log output to a log4j2 configured logger.

It is recommended to configure a separate log file to capture this information by configuring an appender as follows:

.. sourcecode:: xml

    <Logger name="CheckpointAgent" level="info" additivity="false">
        <AppenderRef ref="Checkpoint-Agent-RollingFile-Appender"/>
    </Logger>

.. warning:: You must specify "CheckpointAgent" as the logger name.

In this instance we are specifying a Rolling File appender with archival rotation as follows:

.. sourcecode:: xml

    <RollingFile name="Checkpoint-Agent-RollingFile-Appender"
                 fileName="${log-path}/checkpoints_agent-${date:yyyyMMdd-HHmmss}.log"
                 filePattern="${archive}/checkpoints_agent.%date{yyyy-MM-dd}-%i.log.gz">

        <PatternLayout pattern="[%-5level] %date{ISO8601}{UTC}Z [%t] %c{2}.%method - %msg%n"/>

        <Policies>
            <TimeBasedTriggeringPolicy/>
            <SizeBasedTriggeringPolicy size="100MB"/>
        </Policies>

        <DefaultRolloverStrategy min="1" max="100">
            <Delete basePath="${archive}" maxDepth="1">
                <IfFileName glob="${log-name}*.log.gz"/>
                <IfLastModified age="60d">
                    <IfAny>
                        <IfAccumulatedFileSize exceeds="10 GB"/>
                    </IfAny>
                </IfLastModified>
            </Delete>
        </DefaultRolloverStrategy>

    </RollingFile>

The *log4j2.xml* containing the above configuration must now be be passed to the Corda node JVM along with the agent specification:

.. code-block:: shell

    -Dlog4j.configurationFile=<PATH>/log4j2.xml

Sample output
-------------

Using the *log4j2* configuration described above, the following output is generated to a file called ``checkpoints_agent-<DATE>.log`` under
the Corda node ``logs`` directory for a single flow execution (in this case):

.. sourcecode:: none

    [INFO ] 2019-07-11T18:25:15,723Z [Node thread-1] CheckpointAgent. - [WRITE] Fiber@10000004:[43c7d5c8-aa66-4a98-beed-dc91354d0353][task: co.paralleluniverse.fibers.RunnableFiberTask@4dc8eaf(Fiber@10000004), target: null, scheduler: co.paralleluniverse.fibers.FiberExecutorScheduler@4e468018]
    000:net.corda.node.services.statemachine.FlowStateMachineImpl 21,149

    [INFO ] 2019-07-11T18:19:51,115Z [FiberDeserializationChecker] CheckpointAgent. - [READ] class net.corda.node.services.statemachine.FlowStateMachineImpl
    000:net.corda.node.services.statemachine.FlowStateMachineImpl 21,151
    001:  net.corda.node.services.statemachine.FlowStateMachineImpl 21,149
    002:    java.lang.String 107
    003:      [C 77
    002:    co.paralleluniverse.fibers.Stack 20,932
    003:      [J 278
    003:      [Ljava.lang.Object; 20,054
    004:        net.corda.finance.flows.CashIssueAndPaymentFlow 7,229
    005:          net.corda.core.utilities.ProgressTracker 5,664
    etc ...

    [INFO ] 2019-07-11T18:35:03,198Z [rpc-server-handler-pool-2] CheckpointAgent. - [READ] class net.corda.node.services.statemachine.ErrorState$Clean
    Checkpoint id: 15f16740-4ea2-4e48-bcb3-fd9051d5ba59
    000:net.corda.node.services.statemachine.FlowStateMachineImpl 21,151
    001:  [C 77
    001:  [J 278
    001:  [Ljava.lang.Object; 20,054
    002:    java.util.ArrayList 1,658
    003:      net.corda.core.utilities.ProgressTracker$STARTING 0
    etc ...

Note,

* on WRITE (eg. a checkpoint is being serialized to disk), we have complete information of the checkpoint object including the Fiber it is
  running on and its checkpoint id (43c7d5c8-aa66-4a98-beed-dc91354d0353)

* on READ (eg. a checkpoint is being deserialized from disk), we only have information about the stack class hierarchy.
  Additionally, if we are using the CRaSH shell ``dumpCheckpoints`` command, we also see a flows checkpoint id.

Flow diagnostic process
~~~~~~~~~~~~~~~~~~~~~~~

Lets assume a scenario where we have triggered a flow in a node (eg. node acting as a flow initiator) but the flow does not appear to complete.

For example, you may see the following using the CRaSH shell ``flow watch`` command:

.. sourcecode:: none

    Id                                Flow name                                                           Initiator                        Status
    -----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
    15f16740-4ea2-4e48-bcb3-fd9051d5b Cash Issue And Payment                                              bankUser                         In progress
    1c6c3e59-26aa-4b93-8435-4e34e265e Cash Issue And Payment                                              bankUser                         In progress
    90613d6f-be78-41bd-98e1-33a756c28 Cash Issue And Payment                                              bankUser                         In progress
    43c7d5c8-aa66-4a98-beed-dc91354d0 Cash Issue And Payment                                              bankUser                         In progress
    Waiting for completion or Ctrl-C ...

Note that "In progress" indicates the flows above have not completed (and will have been checkpointed).


1. Check the main corda node log file for *hospitalisation* and/or *flow retry* messages: ``<NODE_BASE>\logs\node-<hostname>.log``

.. sourcecode:: none

    [INFO ] 2019-07-11T17:56:43,227Z [pool-12-thread-1] statemachine.FlowMonitor. - Flow with id 90613d6f-be78-41bd-98e1-33a756c28808 has been waiting for 97904 seconds to receive messages from parties [O=BigCorporation, L=New York, C=US].

.. note:: Always search for the flow id, in this case **90613d6f-be78-41bd-98e1-33a756c28808**

2. From the CRaSH shell run the ``dumpCheckpoints`` command to trigger diagnostics information.

.. sourcecode:: none

    Welcome to the Corda interactive shell.
    Useful commands include 'help' to see what is available, and 'bye' to shut down the node.

    Thu Jul 11 18:56:48 BST 2019>>> run dumpCheckpoints

You will now see an addition line in the main corda node log file as follows:

.. sourcecode:: none

    [INFO ] 2019-07-11T18:02:47,610Z [rpc-server-handler-pool-0] rpc.CheckpointDumper. - Checkpoint agent processing checkpointId: [90613d6f-be78-41bd-98e1-33a756c28808]

And two additional files will appear in the nodes logs directory:

* ``<NODE_BASE>\logs\checkpoints_dump-20190711-180247.zip``
* ``<NODE_BASE>\logs\checkpoints_agent-20190711-185424.log``

3. Unzip the ``<NODE_BASE>\logs\checkpoints_dump-<date>.zip`` file, and you should see a file with a matching flow id as above:
   **CashIssueAndPaymentFlow-90613d6f-be78-41bd-98e1-33a756c28808.json**

   Its contents will contain the following diagnostics information:

   .. sourcecode:: json

       {
         "flowId" : "90613d6f-be78-41bd-98e1-33a756c28808",
         "topLevelFlowClass" : "net.corda.finance.flows.CashIssueAndPaymentFlow",
         "topLevelFlowLogic" : {
           "amount" : "10.00 USD",
           "issueRef" : "MTIzNA==",
           "recipient" : "O=BigCorporation, L=New York, C=US",
           "anonymous" : true,
           "notary" : "O=Notary, L=London, C=GB"
         },
         "flowCallStackSummary" : [
           {
             "flowClass" : "net.corda.finance.flows.CashIssueAndPaymentFlow",
             "progressStep" : "Paying recipient"
           },
           {
             "flowClass" : "net.corda.finance.flows.CashPaymentFlow",
             "progressStep" : "Generating anonymous identities"
           },
           {
             "flowClass" : "net.corda.confidential.SwapIdentitiesFlow",
             "progressStep" : "Awaiting counterparty's anonymous identity"
           }
         ],
         "suspendedOn" : {
           "sendAndReceive" : [
             {
               "session" : {
                 "peer" : "O=BigCorporation, L=New York, C=US",
                 "ourSessionId" : -5024519991106064492
               },
               "sentPayloadType" : "net.corda.confidential.SwapIdentitiesFlow$IdentityWithSignature",
               "sentPayload" : {
                 "identity" : {
                   "class" : "net.corda.core.identity.PartyAndCertificate",
                   "deserialized" : "O=BankOfCorda, L=London, C=GB"
                 },
                 "signature" : "M5DN180OeE4M8jJ3mFohjgeqNYOWXzR6a2PIclJaWyit2uLnmJcZatySoSC12b6e4rQYKIICNFUXRzJnoQTQCg=="
               }
             }
           ],
           "suspendedTimestamp" : "2019-08-12T15:38:39Z",
           "secondsSpentWaiting" : 7
         },
         "flowCallStack" : [
           {
             "flowClass" : "net.corda.finance.flows.CashIssueAndPaymentFlow",
             "progressStep" : "Paying recipient",
             "flowLogic" : {
               "amount" : "10.00 USD",
               "issueRef" : "MTIzNA==",
               "recipient" : "O=BigCorporation, L=New York, C=US",
               "anonymous" : true,
               "notary" : "O=Notary, L=London, C=GB"
             }
           },
           {
             "flowClass" : "net.corda.finance.flows.CashPaymentFlow",
             "progressStep" : "Generating anonymous identities",
             "flowLogic" : {
               "amount" : "10.00 USD",
               "recipient" : "O=BigCorporation, L=New York, C=US",
               "anonymous" : true,
               "issuerConstraint" : [ ],
               "notary" : "O=Notary, L=London, C=GB"
             }
           },
           {
             "flowClass" : "net.corda.confidential.SwapIdentitiesFlow",
             "progressStep" : "Awaiting counterparty's anonymous identity",
             "flowLogic" : {
               "otherSideSession" : {
                 "peer" : "O=BigCorporation, L=New York, C=US",
                 "ourSessionId" : -5024519991106064492
               },
               "otherParty" : null
             }
           }
         ],
         "origin" : {
           "rpc" : "bankUser"
         },
         "ourIdentity" : "O=BankOfCorda, L=London, C=GB",
         "activeSessions" : [ ],
         "errored" : null
       }

4. View the contents of the node agent diagnostics file:

.. sourcecode:: none

    [INFO ] 2019-07-11T18:02:47,615Z [rpc-server-handler-pool-0] CheckpointAgent. - [READ] class net.corda.node.services.statemachine.Checkpoint
    Checkpoint id: 90613d6f-be78-41bd-98e1-33a756c28808
    000:net.corda.node.services.statemachine.Checkpoint 29,200
    001:  net.corda.node.services.statemachine.ErrorState$Clean 0
    001:  net.corda.node.services.statemachine.FlowState$Started 26,061
    002:    net.corda.core.internal.FlowIORequest$SendAndReceive 4,666
    003:      java.util.Collections$SingletonMap 4,536
    004:        net.corda.node.services.statemachine.FlowSessionImpl 500
    005:          net.corda.core.identity.Party 360
    005:          net.corda.node.services.statemachine.SessionId 28
    004:        net.corda.core.serialization.SerializedBytes 3,979
    002:    net.corda.core.serialization.SerializedBytes 21,222
    001:  net.corda.core.context.InvocationContext 905
    002:    net.corda.core.context.Actor 259
    002:    net.corda.core.context.InvocationOrigin$RPC 13
    002:    net.corda.core.context.Trace 398
    001:  net.corda.core.identity.Party 156
    002:    net.i2p.crypto.eddsa.EdDSAPublicKey 45
    002:    net.corda.core.identity.CordaX500Name 92
    001:  java.util.LinkedHashMap 327
    002:    net.corda.node.services.statemachine.SessionState$Initiating 214
    001:  java.util.ArrayList 1,214
    002:    net.corda.node.services.statemachine.SubFlow$Inlined 525
    003:      java.lang.Class 47
    003:      net.corda.node.services.statemachine.SubFlowVersion$CorDappFlow 328
    004:        net.corda.core.crypto.SecureHash$SHA256 118
    005:          [B 33
    002:    net.corda.node.services.statemachine.SubFlow$Initiating 322
    003:      java.lang.Class 39
    003:      net.corda.core.flows.FlowInfo 124
    003:      net.corda.node.services.statemachine.SubFlowVersion$CorDappFlow 11
    002:    net.corda.node.services.statemachine.SubFlow$Initiating 250
    003:      java.lang.Class 41
    003:      net.corda.core.flows.FlowInfo 99
    004:        java.lang.String 91
    005:          [C 85
    003:      net.corda.node.services.statemachine.SubFlowVersion$CoreFlow 28

5. Take relevant recovery action, which may include:

* killing and retrying the flow:

.. sourcecode:: none

    Welcome to the Corda interactive shell.
    Useful commands include 'help' to see what is available, and 'bye' to shut down the node.

    Thu Jul 11 20:24:11 BST 2019>>> flow kill 90613d6f-be78-41bd-98e1-33a756c28808
    [ERROR] 20:24:18+0100 [Node thread-1] corda.flow. - Flow interrupted while waiting for events, aborting immediately {actor_id=bankUser, actor_owning_identity=O=BankOfCorda, L=London, C=GB, actor_store_id=NODE_CONFIG, fiber-id=10000003, flow-id=15f16740-4ea2-4e48-bcb3-fd9051d5ba59, invocation_id=45622dc7-c4cf-4d11-85ad-1c45e0943455, invocation_timestamp=2019-07-11T18:19:40.519Z, origin=bankUser, session_id=02010e15-8e7a-46f7-976b-5e0626451c54, session_timestamp=2019-07-11T18:19:32.285Z, thread-id=176}
    Killed flow [90613d6f-be78-41bd-98e1-33a756c28808]

    Thu Jul 11 20:26:45 BST 2019>>> flow start CashIssueAndPaymentFlow amount: $1000, issueRef: 0x01, recipient: "Bank B", anonymous: false, notary: "Notary Service"

* attempting to perform a graceful shutdown (draining all outstanding flows and preventing others from starting) and re-start of the node:

.. sourcecode:: none

    Welcome to the Corda interactive shell.
    Useful commands include 'help' to see what is available, and 'bye' to shut down the node.

    Thu Jul 11 19:52:56 BST 2019>>> gracefulShutdown

Upon re-start ensure you disable flow draining mode to allow the node to continue to receive requests:

.. sourcecode:: none

    Welcome to the Corda interactive shell.
    Useful commands include 'help' to see what is available, and 'bye' to shut down the node.

    Thu Jul 11 19:52:56 BST 2019>>> run setFlowsDrainingModeEnabled enabled: false

See also :ref:`Flow draining mode <draining-mode>`.

* contacting other participants in the network where their nodes are not responding to an initiated flow.
  The checkpoint dump gives good diagnostics on the reason a flow may be suspended (including the destination peer participant node that is not responding):

.. sourcecode:: json

      {
        "suspendedOn" : {
           "sendAndReceive" : [
             {
               "session" : {
                 "peer" : "O=BigCorporation, L=New York, C=US",
                 "ourSessionId" : -5024519991106064492
               },
               "sentPayloadType" : "net.corda.confidential.SwapIdentitiesFlow$IdentityWithSignature",
               "sentPayload" : {
                 "identity" : {
                   "class" : "net.corda.core.identity.PartyAndCertificate",
                   "deserialized" : "O=BankOfCorda, L=London, C=GB"
                 },
                 "signature" : "M5DN180OeE4M8jJ3mFohjgeqNYOWXzR6a2PIclJaWyit2uLnmJcZatySoSC12b6e4rQYKIICNFUXRzJnoQTQCg=="
               }
             }
           ],
           "suspendedTimestamp" : "2019-08-12T15:38:39Z",
           "secondsSpentWaiting" : 7
        }
      }