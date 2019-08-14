Checkpoint Tooling
================

This page contains information about checkpoint tooling.

Before reading this page, please ensure you understand the mechanics and principles of Corda Flows by reading :doc:`key-concepts-flows` and :doc:`flow-state-machines`.
It is also recommended that you understand the purpose and behaviour of the :doc:`node-flow-hospital` in relation to *checkpoints* and flow recovery.
An advanced explanation of :ref:`*checkpoints* <flow_internals_checkpoints_ref>` within the flow state machine can be found here: :doc:`contributing-flow-internals`.

.. note:: As a recap,

    A flow *checkpoint* is a serialised snapshot of the flow's stack frames and any objects reachable from the stack. Checkpoints are saved to
    the database automatically when a flow suspends or resumes, which typically happens when sending or receiving messages. A flow may be replayed
    from the last checkpoint if the node restarts. Automatic checkpointing is an unusual feature of Corda and significantly helps developers write
    reliable code that can survive node restarts and crashes. It also assists with scaling up, as flows that are waiting for a response can be flushed
    from memory.

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

An additional file will appear in the nodes logs directory:

* ``<NODE_BASE>\logs\checkpoints_dump-20190711-180247.zip``

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

4. Take relevant recovery action, which may include:

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