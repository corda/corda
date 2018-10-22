Configuring Responder Flows
===========================

A flow can be a fairly complex thing that interacts with many backend systems, and so it is likely that different users
of a specific CordApp will require differences in how flows interact with their specific infrastructure.

Corda supports this functionality by providing two mechanisms to modify the behaviour of apps in your node.

Subclassing a Flow
------------------

If you have a workflow which is mostly common, but also requires slight alterations in specific situations, most developers would be familiar
with refactoring into `Base` and `Sub` classes. A simple example is shown below.

java
~~~~

   .. code-block:: java

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

    public class SubResponder extends BaseResponder {

        public SubResponder(FlowSession counterpartySession) {
            super(counterpartySession);
        }

        @Override
        protected String getMessage() {
            return "This is the sub responder";
        }
    }



kotlin
~~~~~~

    .. code-block:: kotlin

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





Corda would detect that both ``BaseResponder`` and ``SubResponder`` are configured for responding to ``Initiator``.
Corda will then calculate the hops to ``FlowLogic`` and select the implementation which is furthest distance, ie: the most subclassed implementation.
In the above example, ``SubResponder`` would be selected as the default responder for ``Initiator``

.. note:: The flows do not need to be within the same CordApp, or package, therefore to customise a shared app you obtained from a third party, you'd write your own CorDapp that subclasses the first."

Overriding a flow via node configuration
----------------------------------------

Whilst the subclassing approach is likely to be useful for most applications, there is another mechanism to override this behaviour.
This would be useful if for example, a specific CordApp user requires such a different responder that subclassing an existing flow
would not be a good solution. In this case, it's possible to specify a hardcoded flow via the node configuration.

The configuration section is named ``flowOverrides`` and it accepts an array of ``overrides``

.. container:: codeset

    .. code-block:: json

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