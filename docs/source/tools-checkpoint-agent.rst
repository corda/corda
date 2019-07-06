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

To run simply pass in the following jar to the JVM used to start a Corda node: ``-javaagent:<PATH>/checkpoint-agent.jar[=arg=value;...]``

The agent can be customised with a number of optional parameters described below.

.. note:: When using the gradle plugin utilities for deploying and running nodes, ensure the checkpoint agent jar is correctly passed to capsule as follows:
    ``-Dcapsule.jvm.args=-javaagent:checkpoint-agent.jar[=arg=value;...]``

Configuration
~~~~~~~~~~~~~

The checkpoint agent can be started with the following optional parameters:

.. code-block:: shell

    checkpoint-agent.jar=[instrumentType=<read|write|read_write>];[instrumentClassname=<CLASSNAME>];[minimumSize=<MIN_SIZE>];[maximumSize=<MAX_SIZ>]

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

See also :ref:`Flow draining <upgrading-cordapps-flow-drains>`.

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

Using the *log4j2* configuration described about, the following output is generated to a file called ``checkpoints_agent-<DATE>.log`` under
the Corda node ``logs`` directory for a single flow execution (in this case):

.. sourcecode:: none

    Fiber@10000006:[800b8dca-a668-4705-a18d-3e71bd9a7f57][task: co.paralleluniverse.fibers.RunnableFiberTask@4dda48db(Fiber@10000006), target: null, scheduler: net.corda.node.services.statemachine.StateMachineManagerImpl$FiberScheduler@5a99377a]
    000:net.corda.node.services.statemachine.FlowStateMachineImpl 15,124
    001:  net.corda.node.services.statemachine.FlowStateMachineImpl 15,122
    002:    java.lang.String 107
    003:      [C 77
    002:    co.paralleluniverse.fibers.Stack 14,065
    003:      [J 181
    003:      [Ljava.lang.Object; 13,678
    004:        net.corda.node.services.FinalityHandler 615
    005:          net.corda.node.services.statemachine.FlowSessionImpl 426
    006:            net.corda.core.identity.Party 314
    007:              net.i2p.crypto.eddsa.EdDSAPublicKey 45
    007:              net.corda.core.identity.CordaX500Name 215
    008:                java.lang.String 12
    009:                  [C 5
    008:                java.lang.String 17
    009:                  [C 11
    008:                java.lang.String 19
    009:                  [C 13
    004:        net.corda.core.flows.ReceiveTransactionFlow 227
    005:          net.corda.core.node.StatesToRecord 1
    004:        org.apache.logging.slf4j.Log4jLogger 53
    004:        net.corda.core.internal.ResolveTransactionsFlow 10,973
    005:          net.corda.core.transactions.SignedTransaction 10,638
    006:            net.corda.core.serialization.SerializedBytes 9,900
    006:            java.util.Collections$UnmodifiableList 734
    007:              java.util.ArrayList 711
    008:                net.corda.core.crypto.TransactionSignature 377
    009:                  [B 65
    009:                  net.i2p.crypto.eddsa.EdDSAPublicKey 45
    009:                  net.corda.core.crypto.SignatureMetadata 72
    008:                net.corda.core.crypto.TransactionSignature 141
    009:                  [B 65
    009:                  net.i2p.crypto.eddsa.EdDSAPublicKey 45
    009:                  net.corda.core.crypto.SignatureMetadata 6
    008:                net.corda.core.crypto.TransactionSignature 141
    009:                  [B 65
    009:                  net.i2p.crypto.eddsa.EdDSAPublicKey 45
    009:                  net.corda.core.crypto.SignatureMetadata 6
    004:        net.corda.core.utilities.UntrustworthyData 41
    004:        net.corda.core.internal.FetchDataFlow$Request$End 0
    004:        net.corda.node.services.statemachine.FlowSessionInternal 563
    005:          net.corda.node.services.statemachine.SessionId 28
    005:          java.util.concurrent.ConcurrentLinkedQueue 1
    005:          net.corda.node.services.statemachine.FlowSessionState$Initiated 177
    006:            net.corda.core.flows.FlowInfo 92
    007:              java.lang.String 47
    008:                [C 41
    006:            net.corda.node.services.statemachine.SessionId 11
    004:        net.corda.node.services.statemachine.ExistingSessionMessage 394
    005:          net.corda.node.services.statemachine.DataSessionMessage 257
    006:            net.corda.core.serialization.SerializedBytes 226
    004:        net.corda.node.services.statemachine.SendOnly 41
    004:        net.corda.node.services.statemachine.FlowStateMachineImpl$suspend$2 0
    004:        kotlin.jvm.internal.Ref$ObjectRef 21
    002:    co.paralleluniverse.strands.Strand$State 1
    002:    net.corda.core.context.InvocationContext 532
    003:      net.corda.core.context.InvocationOrigin$Peer 14
    003:      net.corda.core.context.Trace 305
    004:        net.corda.core.context.Trace$InvocationId 185
    005:          java.lang.String 31
    006:            [C 21
    005:          java.time.Instant 10
    005:          java.lang.String 79
    006:            [C 73
    004:        net.corda.core.context.Trace$SessionId 68
    005:          java.lang.String 21
    006:            [C 15
    002:    net.corda.core.flows.StateMachineRunId 84
    003:      java.util.UUID 56
    002:    java.util.HashMap 50
    003:      kotlin.Pair 32
    002:    net.corda.core.identity.Party 146
    003:      net.i2p.crypto.eddsa.EdDSAPublicKey 45
    003:      net.corda.core.identity.CordaX500Name 82
    004:        java.lang.String 12
    005:          [C 5
    004:        java.lang.String 23
    005:          [C 13
    004:        java.lang.String 23
    005:          [C 13
    002:    net.corda.node.services.statemachine.FlowStateMachineImpl$suspend$1 20