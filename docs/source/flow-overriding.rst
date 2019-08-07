.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Configuring Responder Flows
===========================

A flow can be a fairly complex thing that interacts with many backend systems, and so it is likely that different users
of a specific CorDapp will require differences in how flows interact with their specific infrastructure.

Corda supports this functionality by providing two mechanisms to modify the behaviour of apps in your node.

Subclassing a Flow
------------------

If you have a workflow which is mostly common, but also requires slight alterations in specific situations, most developers would be familiar
with refactoring into `Base` and `Sub` classes. A simple example is shown below.

.. container:: codeset

   .. sourcecode:: kotlin

        @InitiatedBy(Initiator::class)
        open class BaseResponder(internal val otherSideSession: FlowSession) : FlowLogic<Unit>() {
            @Suspendable
            override fun call() {
                otherSideSession.send(getMessage())
            }
            protected open fun getMessage() = "This Is the Legacy Responder"
        }

        @InitiatedBy(Initiator::class)
        class SubResponder(otherSideSession: FlowSession) : BaseResponder(otherSideSession) {
            override fun getMessage(): String {
                return "This is the sub responder"
            }
        }

   .. sourcecode:: java

        @InitiatingFlow
        public class Initiator extends FlowLogic<String> {
            private final Party otherSide;

            public Initiator(Party otherSide) {
                this.otherSide = otherSide;
            }

            @Override
            public String call() throws FlowException {
                return initiateFlow(otherSide).receive(String.class).unwrap((it) -> it);
            }
        }

        @InitiatedBy(Initiator.class)
        public class BaseResponder extends FlowLogic<Void> {
            private FlowSession counterpartySession;

            public BaseResponder(FlowSession counterpartySession) {
                super();
                this.counterpartySession = counterpartySession;
            }

            @Override
            public Void call() throws FlowException {
                counterpartySession.send(getMessage());
                return Void;
            }


            protected String getMessage() {
                return "This Is the Legacy Responder";
            }
        }

        @InitiatedBy(Initiator.class)
        public class SubResponder extends BaseResponder {
            public SubResponder(FlowSession counterpartySession) {
                super(counterpartySession);
            }

            @Override
            protected String getMessage() {
                return "This is the sub responder";
            }
        }


Corda would detect that both ``BaseResponder`` and ``SubResponder`` are configured for responding to ``Initiator``.
Corda will then calculate the hops to ``FlowLogic`` and select the implementation which is furthest distance, ie: the most subclassed implementation.
In the above example, ``SubResponder`` would be selected as the default responder for ``Initiator``

.. note:: The flows do not need to be within the same CorDapp, or package, therefore to customise a shared app you obtained from a third party, you'd write your own CorDapp that subclasses the first.

Overriding a flow via node configuration
----------------------------------------

Whilst the subclassing approach is likely to be useful for most applications, there is another mechanism to override this behaviour.
This would be useful if for example, a specific CorDapp user requires such a different responder that subclassing an existing flow
would not be a good solution. In this case, it's possible to specify a hardcoded flow via the node configuration.

.. note:: A new responder written to override an existing responder must still be annotated with ``@InitiatedBy`` referencing the base initiator.

The configuration section is named ``flowOverrides`` and it accepts an array of ``overrides``

.. container:: codeset

    .. code-block:: none

        flowOverrides {
            overrides=[
                {
                    initiator="net.corda.Initiator"
                    responder="net.corda.BaseResponder"
                }
            ]
        }

The cordform plugin also provides a ``flowOverride`` method within the ``deployNodes`` block which can be used to override a flow. In the below example, we will override
the ``SubResponder`` with ``BaseResponder``

.. container:: codeset

    .. code-block:: groovy

        node {
            name "O=Bank,L=London,C=GB"
            p2pPort 10025
            rpcUsers = ext.rpcUsers
            rpcSettings {
                address "localhost:10026"
                adminAddress "localhost:10027"
            }
            extraConfig = ['h2Settings.address' : 'localhost:10035']
            flowOverride("net.corda.Initiator", "net.corda.BaseResponder")
        }

This will generate the corresponding ``flowOverrides`` section and place it in the configuration for that node.

Modifying the behaviour of @InitiatingFlow(s)
---------------------------------------------

It is likely that initiating flows will also require changes to reflect the different systems that are likely to be encountered.
At the moment, corda provides the ability to subclass an Initiator, and ensures that the correct responder will be invoked.
In the below example, we will change the behaviour of an Initiator from filtering Notaries out from comms, to only communicating with Notaries

    .. code-block:: kotlin

        @InitiatingFlow
        @StartableByRPC
        @StartableByService
        open class BaseInitiator : FlowLogic<String>() {
            @Suspendable
            override fun call(): String {
                val partiesToTalkTo = serviceHub.networkMapCache.allNodes
                        .filterNot { it.legalIdentities.first() in serviceHub.networkMapCache.notaryIdentities }
                        .filterNot { it.legalIdentities.first().name == ourIdentity.name }.map { it.legalIdentities.first() }
                val responses = ArrayList<String>()
                for (party in partiesToTalkTo) {
                    val session = initiateFlow(party)
                    val received = session.receive<String>().unwrap { it }
                    responses.add(party.name.toString() + " responded with backend: " + received)
                }
                return "${getFLowName()} received the following \n" + responses.joinToString("\n") { it }
            }

            open fun getFLowName(): String {
                return "Normal Computer"
            }
        }

        @StartableByRPC
        @StartableByService
        class NotaryOnlyInitiator : BaseInitiator() {
            @Suspendable
            override fun call(): String {
                return "Notary Communicator received:\n" + serviceHub.networkMapCache.notaryIdentities.map {
                    "Notary: ${it.name.organisation} is using a " + initiateFlow(it).receive<String>().unwrap { it }
                }.joinToString("\n") { it }
            }

.. warning:: The subclass must not have the @InitiatingFlow annotation.

Corda will use the first annotation detected in the class hierarchy to determine which responder should be invoked. So for a Responder similar to

    .. code-block:: kotlin

        @InitiatedBy(BaseInitiator::class)
        class BobbyResponder(othersideSession: FlowSession) : BaseResponder(othersideSession) {
            override fun getMessageFromBackend(): String {
                return "Robert'); DROP TABLE STATES;"
            }
        }

it would be possible to invoke either ``BaseInitiator`` or ``NotaryOnlyInitiator`` and ``BobbyResponder`` would be used to reply.

.. warning:: You must ensure the sequence of sends/receives/subFlows in a subclass are compatible with the parent.
