Checkpoint Agent
================

A flow *checkpoint* is a serialised snapshot of the flow's stack frames and any objects reachable from the stack. Checkpoints are saved to
the database automatically when a flow suspends or resumes, which typically happens when sending or receiving messages. A flow may be replayed
from the last checkpoint if the node restarts. Automatic checkpointing is an unusual feature of Corda and significantly helps developers write
reliable code that can survive node restarts and crashes. It also assists with scaling up, as flows that are waiting for a response can be flushed
from memory.

The Checkpoint Agent is a diagnostics tool that can be used to output the type and size of flow *checkpoints* at Corda runtime.

For a given flow *checkpoint*, the agent outputs:

    1. Information about the checkpoint such as a Fiber id (associated with a flow) that can be used to correlate with that flows lifecycle details in the main Corda logs.
    2. A nested hierarchical view of its reachable objects (indented and tagged with depth size) and their associated sizes.

Diagnostics information is written to standard log files (eg. log4j2 configured logger).

This tool is particularly useful when used in conjunction with the ``dumpCheckpoints`` CRaSH shell command to troubleshoot and identify potential
problems associated with checkpoints that are preventing flows from completing.

The checkpoint agent can be downloaded from `here <https://software.r3.com/artifactory/corda-releases/net/corda/corda-tools-checkpoint-agent/>`_.

To run simply pass in the following jar to the JVM used to start a Corda node: ``-javaagent:<PATH>/checkpoint-agent.jar[=arg=value,...]``

.. warning:: You will also need to increase the default JVM heap size to at least 1Gb by passing `-Xmx1024m` to the JVM on startup.

The agent can be customised with a number of optional parameters described below.

.. note:: When using the gradle plugin utilities for deploying and running nodes, ensure the checkpoint agent jar is correctly passed to capsule as follows:
    ``-Dcapsule.jvm.args=-javaagent:checkpoint-agent.jar[=arg=value,...]``

Configuration
~~~~~~~~~~~~~

The checkpoint agent can be started with the following optional parameters:

.. code-block:: shell

    checkpoint-agent.jar=[instrumentType=<read|write|read_write>],[instrumentClassname=<CLASSNAME>],[minimumSize=<MIN_SIZE>],[maximumSize=<MAX_SIZ>]

* ``instrumentType``: whether to output checkpoints on read, write or both. Possible values: [read, write, read_write]. Default: read_write.
* ``instrumentClassname``: specify the base type of objects to log. The default setting is to process all *Flow* object types. Default: net.corda.node.services.statemachine.FlowStateMachineImpl.
* ``minimumSize``: specifies the minimum size (in bytes) of objects to log. Default: 8192 bytes
* ``maximumSize``: specifies the maximum size (in bytes) of objects to log. Default: 1024000 bytes

These arguments are passed to the JVM along with the agent specification. For example:

.. code-block:: shell

    -javaagent:<PATH>/checkpoint-agent.jar=instrumentClassname=net.corda.vega.flows.SimmFlow,instrumentType=read,minimumSize=10240,maximumSize=512000

.. note:: Arguments may be passed into the agent in any order and should **not** contain spaces between arguments.

Checkpoint Dump support
~~~~~~~~~~~~~~~~~~~~~~~

Information about checkpointed flows can be retrieved from the shell. Calling ``dumpCheckpoints`` will create a zip file inside the node's
``log`` directory. This zip will contain a JSON representation of each checkpointed flow. This information can then be used to determine the
state of stuck flows or flows that experienced internal errors and were kept in the node for manual intervention.

When used in combination with the ``dumpCheckpoints`` shell command, the checkpoint agent will automatically output additional diagnostic
information for all checkpoints dumped by the aforementioned tool.

You should therefore see two different output files upon invoking the checkpoint dumper command:

* ``<NODE_BASE>\logs\checkpoints_dump-<date>.zip`` contains zipped JSON representation of checkpoints (from ``dumpCheckpoints`` shell command)
* ``<NODE_BASE>\logs\checkpoints_agent-<date>.log`` contains output from this agent tool (types and sizes of a checkpoint stack)

.. note:: you will only see a separate `checkpoints_agent-<date>.log` file if you configure a separate log4j logger as described below.
   Otherwise all diagnostics logging will be routed to the standard Corda node log file: ``node-<hostname>.log``.

If you **only** wish to log checkpoint data for failing flows, start the checkpoint agent with the following arguments:

.. code-block:: shell

    checkpoint-agent.jar=instrumentType=read,instrumentClassname=NONE

and use the ``dumpCheckpoints`` shell command to trigger diagnostics collection.

Logging configuration
~~~~~~~~~~~~~~~~~~~~~

The agent will log output to a log4j2 configured logger.

It is recommended to configure a separate log file to capture this information by configuring an appender as follows:

.. sourcecode:: none

    <Logger name="CheckpointAgent" level="info" additivity="false">
        <AppenderRef ref="Checkpoint-Agent-RollingFile-Appender"/>
    </Logger>

.. warning:: you must specify "CheckpointAgent" as the logger name.

In this instance we are specifying a Rolling File appender with archival rotation as follows:

.. sourcecode:: none

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
~~~~~~~~~~~~~

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
    006:            java.util.ArrayList 1,658
    007:              kotlin.Pair 115
    008:                java.lang.Integer 17
    008:                net.corda.core.utilities.ProgressTracker$STARTING 0
    007:              kotlin.Pair 249
    008:                java.lang.Integer 3
    008:                net.corda.core.utilities.ProgressTracker$Step 185
    009:                  java.lang.String 111
    010:                    [C 101
    etc ...

    [INFO ] 2019-07-11T18:35:03,198Z [rpc-server-handler-pool-2] CheckpointAgent. - [READ] class net.corda.node.services.statemachine.ErrorState$Clean
    Checkpoint id: 15f16740-4ea2-4e48-bcb3-fd9051d5ba59
    000:net.corda.node.services.statemachine.FlowStateMachineImpl 21,151
    001:  [C 77
    001:  [J 278
    001:  [Ljava.lang.Object; 20,054
    002:    java.util.ArrayList 1,658
    003:      net.corda.core.utilities.ProgressTracker$STARTING 0

Note,

* on WRITE (eg. a checkpoint is being serialized to disk), we have complete information of the checkpoint object including the Fiber it is
  running on and its checkpoint id (43c7d5c8-aa66-4a98-beed-dc91354d0353)

* on READ (eg. a checkpoint is being deserialized from disk), we only have information about the stack class hierarchy.
  Additionally, if we are using the CRaSH shell ``dumpCheckpoints`` command, we also see a flows checkpoint id.

Flow diagnostic process
~~~~~~~~~~~~~~~~~~~~~~~
Firstly, please ensure you understand the mechanics and principles of Corda Flows by reading :doc:`key-concepts-flows` and :doc:`flow-state-machines`.

Lets assume a scenario where have triggered a flow from a node (eg. node acting as a flow initiator) but the flow does not appear to complete.

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

.. note:: always search for the flow id, in this case **90613d6f-be78-41bd-98e1-33a756c28808**

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
   **CashIssueAndPaymentFlow-90613d6f-be78-41bd-98e1-33a756c28808.jsos**

   It contents will contain the following diagnostics information:

.. sourcecode:: none
    {
      "id" : "90613d6f-be78-41bd-98e1-33a756c28808",
      "flowLogicClass" : "net.corda.finance.flows.CashIssueAndPaymentFlow",
      "flowLogic" : {
        "amount" : "200.00 USD",
        "issueRef" : "AQ==",
        "recipient" : "O=BigCorporation, L=New York, C=US",
        "anonymous" : true,
        "notary" : "O=Notary Service, L=Zurich, C=CH"
      },
      "flowCallStack" : [
        {
          "flowClass" : "net.corda.confidential.SwapIdentitiesFlow",
          "progressStep" : "Awaiting counterparty's anonymous identity"
        },
        {
          "flowClass" : "net.corda.finance.flows.CashPaymentFlow",
          "progressStep" : "Generating anonymous identities"
        },
        {
          "flowClass" : "net.corda.finance.flows.CashIssueAndPaymentFlow",
          "progressStep" : "Paying recipient"
        }
      ],
      "suspendedOn" : {
        "sendAndReceive" : [
          {
            "session" : {
              "peer" : "O=BigCorporation, L=New York, C=US",
              "ourSessionId" : 1443438003030966253
            },
            "sentPayloadType" : "net.corda.confidential.SwapIdentitiesFlow$IdentityWithSignature",
            "sentPayload" : {
              "identity" : {
                "class" : "net.corda.core.identity.PartyAndCertificate",
                "deserialized" : "O=BankOfCorda, L=London, C=GB"
              },
              "signature" : "t+7hyUnQE08n3ST4krA/7fi1R8ItdrGvpeEbMFgTBDCHibMWiKo/NaTSVUdfwPmsEtl1PFx0MHz5rtRQ+XuEBg=="
            }
          }
        ],
        "suspendedTimestamp" : "2019-07-10T14:44:58",
        "secondsSpentWaiting" : 98268
      },
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
    006:            net.i2p.crypto.eddsa.EdDSAPublicKey 45
    006:            net.corda.core.identity.CordaX500Name 261
    007:              java.lang.String 36
    008:                [C 5
    007:              java.lang.String 23
    008:                [C 17
    007:              java.lang.String 35
    008:                [C 29
    005:          net.corda.node.services.statemachine.SessionId 28
    004:        net.corda.core.serialization.SerializedBytes 3,979
    002:    net.corda.core.serialization.SerializedBytes 21,222
    001:  net.corda.core.context.InvocationContext 905
    002:    net.corda.core.context.Actor 259
    003:      net.corda.core.context.Actor$Id 41
    004:        java.lang.String 27
    005:          [C 17
    003:      net.corda.core.identity.CordaX500Name 84
    004:        java.lang.String 12
    005:          [C 5
    004:        java.lang.String 19
    005:          [C 13
    004:        java.lang.String 29
    005:          [C 23
    003:      net.corda.core.context.AuthServiceId 58
    004:        java.lang.String 33
    005:          [C 23
    002:    net.corda.core.context.InvocationOrigin$RPC 13
    002:    net.corda.core.context.Trace 398
    003:      net.corda.core.context.Trace$InvocationId 185
    004:        java.lang.String 31
    005:          [C 21
    004:        java.time.Instant 10
    004:        java.lang.String 79
    005:          [C 73
    003:      net.corda.core.context.Trace$SessionId 159
    004:        java.lang.String 21
    005:          [C 15
    004:        java.time.Instant 10
    004:        java.lang.String 79
    005:          [C 73
    001:  net.corda.core.identity.Party 156
    002:    net.i2p.crypto.eddsa.EdDSAPublicKey 45
    002:    net.corda.core.identity.CordaX500Name 92
    003:      java.lang.String 12
    004:        [C 5
    003:      java.lang.String 23
    004:        [C 13
    003:      java.lang.String 33
    004:        [C 23
    001:  java.util.LinkedHashMap 327
    002:    net.corda.node.services.statemachine.SessionState$Initiating 214
    003:      java.util.Collections$EmptyList 0
    003:      java.lang.String 89
    004:        [C 83
    001:  java.util.ArrayList 1,214
    002:    net.corda.node.services.statemachine.SubFlow$Inlined 525
    003:      java.lang.Class 47
    003:      net.corda.node.services.statemachine.SubFlowVersion$CorDappFlow 328
    004:        net.corda.core.crypto.SecureHash$SHA256 118
    005:          [B 33
    004:        java.lang.String 79
    005:          [C 73
    002:    net.corda.node.services.statemachine.SubFlow$Initiating 322
    003:      java.lang.Class 39
    003:      net.corda.core.flows.FlowInfo 124
    004:        java.lang.String 79
    005:          [C 73
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

See also :ref:`Flow draining mode <draining-mode>` and :ref:`Upgrading CorDapps <upgrading-cordapps-flow-drains>`.

* contacting other participants in the network where their nodes are not responding to an initiated flow.
  The checkpoint dump gives good diagnostics on the reason a flow may be suspended (including the destination peer participant who is not responding):

.. sourcecode:: none

      "suspendedOn" : {
        "sendAndReceive" : [
          {
            "session" : {
              "peer" : "O=BigCorporation, L=New York, C=US",
              "ourSessionId" : 1443438003030966253
            },
            "sentPayloadType" : "net.corda.confidential.SwapIdentitiesFlow$IdentityWithSignature",
            "sentPayload" : {
              "identity" : {
                "class" : "net.corda.core.identity.PartyAndCertificate",
                "deserialized" : "O=BankOfCorda, L=London, C=GB"
              },
              "signature" : "t+7hyUnQE08n3ST4krA/7fi1R8ItdrGvpeEbMFgTBDCHibMWiKo/NaTSVUdfwPmsEtl1PFx0MHz5rtRQ+XuEBg=="
            }
          }