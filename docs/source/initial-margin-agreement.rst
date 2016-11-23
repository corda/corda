Initial Margin Agreements
=========================

This app is a demonstration of how Corda can be used for the real world requirement of initial margin calculation and
agreement; featuring the integration of complex and industry proven third party libraries into Corda nodes.

SIMM Introduction
-----------------

SIMM is an acronym for "Standard Initial Margin Model". It is effectively the calculation of a "margin" that is paid
by one party to another when they agree a trade on certain types of transaction. This margin is
paid such that, in the event of one of the counterparties suffering a credit event
(a financial term and a polite way to say defaulting, not paying the debts that are due, or potentially even bankruptcy),
then the party that is owed any sum already has some of the amount that it should have been paid. This payment to the
receiving party is a preventative measure in order to reduce the risk of a potentially catastrophic default domino
effect that caused the `Great Financial Crisis <https://en.wikipedia.org/wiki/Financial_crisis_of_2007%E2%80%932008>`_,
as it means that they can be assured that if they need to pay another party, they will have a proportion of the funds
that they have been relying on.

To enact this, in September 2016, the ISDA committee - with full backing from various governing bodies -
`issued a ruling on what is known as the ISDA SIMM ™ model <http://www2.isda.org/news/isda-simm-deployed-today-new-industry-standard-for-calculating-initial-margin-widely-adopted-by-market-participants>`_,
a way of fairly and consistently calculating this margin. Any parties wishing to trade a financial product that is
covered under this ruling would, independently, use this model and calculate their margin payment requirement,
agree it with their trading counterparty and then pay (or receive, depending on the results of this calculation)
this amount. In the case of disagreement that is not resolved in a timely fashion, this payment would increase
and so therefore it is in the parties interest to reach agreement in a short as time frame as possible.

To be more accurate, the SIMM calculation is not performed on just one trade - it is calculated on an aggregate of
intermediary values (which in this model are sensitivities to risk factors) from a portfolio of trades; therefore
the input to a SIMM is actually this data, not the individual trades itself.

Also note that implementations of the SIMM are actually protected and subject to license restrictions by ISDA
(this is due to the model itself being protected). We were fortunate enough to technically partner with
`OpenGamma <http://www.opengamma.com>`_  who allowed us to demonstrate the SIMM process using their proprietary model.
In the source code released, we have replaced their analytics engine with very simple stub functions that allow
the process to run and can easily be swapped out in place for their real libraries.

Process steps
-------------

Preliminaries
    - Ensure that there are a number of live trades with another party financial products that are covered under the
      ISDA SIMM agreement (if none, then use the demo to enter some simple trades as described below).

Initial Margin Agreement Process
    - Agree that one will be performing the margining calculation against a portfolio of trades with another party, and agree the trades in that portfolio. In practice, one node will start the flow but it does not matter which node does.
    - Individually (at the node level), identify the data (static, reference etc) one will need in order to be able to calculate the metrics on those trades
    - Confirm with the other counterparty the dataset from the above set
    - Calculate any intermediary steps and values needed for the margin calculation (ie sensitivities to risk factors)
    - Agree on the results of these steps
    - Calculate the initial margin
    - Agree on the calculation of the above with the other party
    - In practice, pay (or receive) this margin (omitted for the sake of complexity for this example)


Running the app
---------------

The demonstration can be run in two ways - via IntelliJ (which will allow you to add breakpoints, debug, etc), or via gradle and the command line.

Run with IntelliJ::

    1. Open the `cordapp-samples` project with IntelliJ
    2. Run the shared run configuration "SIMM Valuation Demo"
    3. Browse to http://localhost:10005/web/simmvaluationdemo

Run via CLI::

    1. Navigate to the `cordapp-samples` directory in your shell
    2. Run the gradle target `deployNodes` (ie; ./gradlew deployNodes for Unix or gradlew.bat on Windows)
        1. Unix: `cd simm-valuation-demo/build/nodes && ./runnodes`.
        2. Windows: `cd simm-valuation-demo/build/nodes & runnodes.bat`
    4. Browse to http://localhost:10005/web/simmvaluationdemo
